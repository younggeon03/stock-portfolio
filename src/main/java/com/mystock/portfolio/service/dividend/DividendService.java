package com.mystock.portfolio.service.dividend;

import com.mystock.portfolio.domain.DartCorpCode;
import com.mystock.portfolio.domain.DividendEvent;
import com.mystock.portfolio.domain.DividendEventRepository;
import com.mystock.portfolio.domain.DividendFetch;
import com.mystock.portfolio.domain.DividendFetchRepository;
import com.mystock.portfolio.external.dart.DartApiClient;
import com.mystock.portfolio.external.dart.DartDisclosureRow;
import com.mystock.portfolio.external.dart.DartFinancialService;
import com.mystock.portfolio.external.dart.DartProperties;
import com.mystock.portfolio.external.dart.DividendParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 배당 캘린더. 국내 종목의 배당결정 공시(DART)를 모아 기준일·지급일·주당 금액을 보여준다.
 *
 * ★ DART 는 하루에 종목당 한 번만 부른다
 * 공개 화면이라 같은 종목을 여러 사람이 찾는다. 하루 2만 건 한도를 지키려고 종목별로 마지막 조회 시각을 남기고,
 * 하루가 안 지났으면 DB 에 쌓인 것만 보여준다. 공시 본문은 접수번호로 한 번 받으면 다시 받지 않는다.
 *
 * ★ 정정 공시
 * 같은 종목·같은 기준일·같은 구분이면 접수번호가 큰(나중) 공시가 이긴다. 원본은 남겨 둔다.
 *
 * ★ 지어내지 않는다
 * 다음 배당을 "예상" 하지 않는다. 공시된 것만 보여준다. 결산배당 지급일이 아직 "-" 면 미정으로 둔다.
 */
@Service
public class DividendService {

    private static final Logger log = LoggerFactory.getLogger(DividendService.class);

    /** 한 번에 찾는 종목 수. 공개 주소라 많이 넣어 DART 를 몰아 부르지 못하게 */
    public static final int MAX_STOCKS = 20;
    /** 얼마나 옛 공시까지 보나. 분기배당 넷 + 결산 하나가 들어가도록 넉넉히 */
    static final int LOOKBACK_DAYS = 430;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final DartApiClient dart;
    private final DartFinancialService corpLookup;
    private final DartProperties properties;
    private final DividendEventRepository events;
    private final DividendFetchRepository fetches;
    private final com.mystock.portfolio.service.symbol.SymbolMasterService symbols;

    public DividendService(DartApiClient dart, DartFinancialService corpLookup, DartProperties properties,
                           DividendEventRepository events, DividendFetchRepository fetches,
                           com.mystock.portfolio.service.symbol.SymbolMasterService symbols) {
        this.dart = dart;
        this.corpLookup = corpLookup;
        this.properties = properties;
        this.events = events;
        this.fetches = fetches;
        this.symbols = symbols;
    }

    /** 입력 한 줄: 종목코드 또는 이름, 그리고 (있으면) 보유 주식 수 */
    public record Query(String codeOrName, Long shares) {
    }

    public record Payment(String stockCode, String corpName, String kind, String cashType,
                          BigDecimal perShareCommon, BigDecimal perSharePreferred, BigDecimal yieldCommon,
                          LocalDate recordDate, LocalDate payDate, LocalDate announcedDate, boolean upcoming,
                          Long shares, BigDecimal expectedAmount, String sourceUrl) {
    }

    public record Stock(String query, String stockCode, String corpName, List<Payment> payments,
                        BigDecimal trailingPerShare, String error) {
    }

    public record Calendar(List<Stock> stocks, List<Payment> upcoming, LocalDate today) {
    }

    public Calendar calendar(List<Query> queries) {
        if (!properties.hasKey()) {
            throw new IllegalStateException("DART_API_KEY 가 없어 배당 공시를 받을 수 없습니다");
        }
        LocalDate today = LocalDate.now(KST);
        List<Stock> stocks = new ArrayList<>();
        for (Query q : queries) {
            stocks.add(stock(q, today));
        }
        List<Payment> upcoming = stocks.stream().flatMap(s -> s.payments().stream())
                .filter(Payment::upcoming)
                .sorted(Comparator.comparing((Payment p) -> p.payDate() != null ? p.payDate() : p.recordDate()))
                .toList();
        return new Calendar(stocks, upcoming, today);
    }

    /**
     * 종목코드 → DART 공식 이름 → 흔히 쓰는 이름(종목 마스터, 정확히 같을 때만) 순서로 찾는다.
     * DART 이름은 "현대자동차" 인데 사람들은 "현대차" 라고 쓴다. 비슷한 이름으로 넘겨짚지는 않는다
     * ("삼송전자" 를 삼영전자로 바꾸면 남의 종목 배당을 보여주게 된다).
     */
    private Optional<DartCorpCode> resolve(String codeOrName) {
        Optional<DartCorpCode> direct = corpLookup.findCorp(codeOrName);
        if (direct.isPresent()) {
            return direct;
        }
        try {
            com.mystock.portfolio.service.symbol.SymbolMatch m = symbols.findByName(codeOrName);
            if (m.found() && m.confidence() >= 100 && m.symbol() != null && m.symbol().matches("\\d{6}")) {
                return corpLookup.findCorp(m.symbol());
            }
        } catch (Exception e) {
            log.debug("종목 마스터로 이름 찾기 실패: {}", e.getMessage());
        }
        return Optional.empty();
    }

    private Stock stock(Query q, LocalDate today) {
        Optional<DartCorpCode> corp = resolve(q.codeOrName());
        if (corp.isEmpty()) {
            return new Stock(q.codeOrName(), null, null, List.of(), null,
                    "상장사를 찾지 못했습니다. 6자리 종목코드나 정확한 회사 이름을 넣어 주세요");
        }
        DartCorpCode c = corp.get();
        try {
            refreshIfStale(c, today);
        } catch (Exception e) {
            // DART 가 막혀도 이미 받아 둔 것은 보여준다
            log.warn("배당 공시 받기 실패 ({}): {}", c.getCorpName(), e.getMessage());
        }
        List<DividendEvent> rows = events.findByStockCodeAndRecordDateGreaterThanEqualOrderByRecordDateDesc(
                c.getStockCode(), today.minusDays(LOOKBACK_DAYS));
        List<Payment> payments = latestPerRecord(rows).stream().map(e -> payment(e, q.shares(), today)).toList();
        return new Stock(q.codeOrName(), c.getStockCode(), c.getCorpName(), payments,
                trailingPerShare(payments, today), null);
    }

    /** 하루가 지났으면 DART 공시 목록을 다시 보고, 처음 보는 배당결정 공시만 본문을 받는다 */
    private void refreshIfStale(DartCorpCode c, LocalDate today) {
        Optional<DividendFetch> last = fetches.findById(c.getStockCode());
        if (last.isPresent() && last.get().getFetchedAt().isAfter(LocalDateTime.now(KST).minusHours(24))) {
            return;
        }
        List<DartDisclosureRow> list = dart.disclosures(c.getCorpCode(), today.minusDays(LOOKBACK_DAYS), today, "I");
        for (DartDisclosureRow row : list) {
            if (!isDividendDecision(row.reportName()) || events.existsById(row.receiptNo())) {
                continue;
            }
            Optional<DividendParser.Dividend> d = DividendParser.parse(dart.documentText(row.receiptNo()));
            if (d.isEmpty()) {
                log.info("배당 공시를 읽지 못해 건너뜀: {} {}", c.getCorpName(), row.receiptNo());
                continue;
            }
            DividendParser.Dividend v = d.get();
            events.save(new DividendEvent(row.receiptNo(), c.getStockCode(), c.getCorpName(), v.kind(), v.cashType(),
                    v.perShareCommon(), v.perSharePreferred(), v.yieldCommon(), v.recordDate(), v.payDate(),
                    v.boardDate(), LocalDate.parse(row.receiptDate(), DateTimeFormatter.BASIC_ISO_DATE),
                    row.reportName().contains("정정")));
        }
        fetches.save(new DividendFetch(c.getStockCode()));
    }

    /** "현금ㆍ현물배당결정", "현금배당결정", "[기재정정]현금ㆍ현물배당결정". 자회사 배당 공시 등은 뺀다 */
    static boolean isDividendDecision(String reportName) {
        if (reportName == null) {
            return false;
        }
        String n = reportName.replace(" ", "");
        return n.contains("배당결정") && !n.contains("자회사") && !n.contains("주요종속회사");
    }

    /** 같은 기준일·구분이면 접수번호가 큰(나중) 공시만 */
    static List<DividendEvent> latestPerRecord(List<DividendEvent> rows) {
        Map<String, DividendEvent> latest = new LinkedHashMap<>();
        for (DividendEvent e : rows) {
            String key = e.getRecordDate() + "|" + e.getKind();
            latest.merge(key, e, (a, b) -> a.getRceptNo().compareTo(b.getRceptNo()) >= 0 ? a : b);
        }
        return latest.values().stream().sorted(Comparator.comparing(DividendEvent::getRecordDate).reversed()).toList();
    }

    static Payment payment(DividendEvent e, Long shares, LocalDate today) {
        boolean upcoming = (e.getPayDate() != null && !e.getPayDate().isBefore(today))
                || (e.getPayDate() == null && !e.getRecordDate().isBefore(today.minusDays(120)));
        BigDecimal expected = shares == null ? null : e.getPerShareCommon().multiply(BigDecimal.valueOf(shares));
        return new Payment(e.getStockCode(), e.getCorpName(), e.getKind(), e.getCashType(), e.getPerShareCommon(),
                e.getPerSharePreferred(), e.getYieldCommon(), e.getRecordDate(), e.getPayDate(), e.getAnnouncedDate(),
                upcoming, shares, expected, "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=" + e.getRceptNo());
    }

    /** 최근 1년(기준일) 주당 배당 합계. 정해진 것만 더한다 */
    static BigDecimal trailingPerShare(List<Payment> payments, LocalDate today) {
        return payments.stream()
                .filter(p -> p.recordDate().isAfter(today.minusYears(1)))
                .map(Payment::perShareCommon)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
