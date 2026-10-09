package com.mystock.portfolio.external.filing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mystock.portfolio.domain.StoredFinancials;
import com.mystock.portfolio.domain.StoredFinancialsRepository;
import com.mystock.portfolio.external.dart.DartFinancialService;
import com.mystock.portfolio.external.edgar.EdgarFinancialService;
import com.mystock.portfolio.external.toss.TossMarketDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 종목이 어느 나라 것인지 보고 공시처를 고른다. 한국은 DART, 미국은 SEC EDGAR.
 *
 * 부르는 쪽(기업분석, 미리보기 주소)이 공시처를 몰라도 되게 여기서 한 번만 가른다.
 * ETF 는 어느 쪽에도 기업 재무가 없으니 부르지 않는다.
 *
 * 과거 PER·PBR 도 여기서 붙인다. 공시처는 재무만 알고 주가는 토스가 알아서, 둘을 합치는 자리가 여기다.
 */
@Service
public class FilingService {

    private static final Logger log = LoggerFactory.getLogger(FilingService.class);

    /** 저장된 공시 재무를 이만큼 지나면 공시처에서 다시 받는다. 새 분기 공시가 하루 안에 반영된다 */
    static final Duration REFRESH_AFTER = Duration.ofDays(1);

    private final DartFinancialService dart;
    private final EdgarFinancialService edgar;
    private final TossMarketDataService marketData;
    private final StoredFinancialsRepository store;
    private final ObjectMapper objectMapper;

    public FilingService(DartFinancialService dart, EdgarFinancialService edgar, TossMarketDataService marketData,
                         StoredFinancialsRepository store, ObjectMapper objectMapper) {
        this.dart = dart;
        this.edgar = edgar;
        this.marketData = marketData;
        this.store = store;
        this.objectMapper = objectMapper;
    }

    /**
     * @param marketCountry KR 또는 US
     * @param shares        지금 발행주식수. 없으면 주당 지표가 비거나(DART) SEC 표지 값을 쓴다(EDGAR)
     * @param price         현재가 (거래 통화). 없으면 PER·PBR 이 빈다
     * @return 공시처에 없거나, 키가 없거나, 실패하면 비어 있다. 예외를 던지지 않는다
     */
    public Optional<CompanyFinancials> find(String symbol, String marketCountry, boolean fund,
                                            BigDecimal shares, BigDecimal price) {
        if (fund || !("KR".equals(marketCountry) || "US".equals(marketCountry))) {
            return Optional.empty();
        }
        // 재무(주가 없는 부분)는 DB 에서. PER·PBR 만 지금 주가로 잰다
        return stored(symbol, marketCountry, shares).map(f -> f.withPrice(price));
    }

    /**
     * 저장된 공시 재무. 없거나 하루가 지났으면 공시처에서 다시 받아 저장한다.
     * 다시 받기가 실패하면 저장된 것(하루 넘은 것이라도)을 쓴다. 공시 재무는 며칠 묵어도 틀리지 않는다.
     * 과거 PER·PBR 을 못 붙였던 것(그때 토스가 막힘)은 읽을 때 다시 붙여 본다.
     */
    private Optional<CompanyFinancials> stored(String symbol, String country, BigDecimal shares) {
        Optional<StoredFinancials> saved = store.findById(symbol);
        boolean fresh = saved.isPresent()
                && saved.get().getFetchedAt().plus(REFRESH_AFTER).isAfter(LocalDateTime.now());
        if (fresh) {
            CompanyFinancials f = read(saved.get());
            if (f != null && !saved.get().isHasHistory() && historyRetryDue(symbol)) {
                CompanyFinancials withHist = withHistory(symbol, f);
                if (hasHistory(withHist)) {
                    save(symbol, country, withHist, saved.get().getFetchedAt());
                }
                return Optional.of(withHist);
            }
            if (f != null) {
                return Optional.of(f);
            }
        }

        Optional<CompanyFinancials> fetched = fetch(symbol, country, shares);
        if (fetched.isPresent()) {
            CompanyFinancials f = withHistory(symbol, fetched.get());
            save(symbol, country, f, LocalDateTime.now());
            return Optional.of(f);
        }
        if (saved.isPresent()) {
            log.info("{} 공시 재무를 새로 받지 못해 {} 에 받은 것을 씁니다", symbol, saved.get().getFetchedAt());
            return Optional.ofNullable(read(saved.get()));
        }
        return Optional.empty();
    }

    /** 야간 배치가 종목 하나를 미리 받은 결과 */
    public enum Prefetch {
        /** 새로 받아 저장했다 */
        REFRESHED,
        /** 새로 받지 못해 저장본을 그대로 뒀다 */
        KEPT_OLD,
        /** 공시처에 없고 저장본도 없다 (상장 직후, 매핑에 없는 종목) */
        NOT_FOUND,
        /** ETF 이거나 한국·미국 종목이 아니라 공시 재무가 없다 */
        NOT_APPLICABLE
    }

    /**
     * 신선도와 상관없이 공시처에서 다시 받아 저장한다. 야간 배치가 쓴다.
     *
     * 낮에 분석 버튼을 누를 때 공시처(DART 는 보고서마다 여러 번)·과거 종가를 부르지 않고
     * 저장본을 바로 읽게 하려는 것이다. 받기가 실패하면 저장본을 지우지 않는다. 묵은 재무가 없는 재무보다 낫다.
     *
     * @param shares 지금 발행주식수. 부르는 쪽이 토스에서 받아 넘긴다. 없으면 주당 지표가 빈 채로 저장되므로
     *               배치는 주식수를 못 받은 종목을 아예 부르지 않는다
     */
    public Prefetch refresh(String symbol, String marketCountry, boolean fund, BigDecimal shares) {
        if (fund || !("KR".equals(marketCountry) || "US".equals(marketCountry))) {
            return Prefetch.NOT_APPLICABLE;
        }
        Optional<CompanyFinancials> fetched = fetch(symbol, marketCountry, shares);
        if (fetched.isPresent()
                && save(symbol, marketCountry, withHistory(symbol, fetched.get()), LocalDateTime.now())) {
            return Prefetch.REFRESHED;
        }
        return store.existsById(symbol) ? Prefetch.KEPT_OLD : Prefetch.NOT_FOUND;
    }

    /** 주가 없이 받는다. 주가는 읽을 때마다 붙인다. 예외를 던지지 않는다 */
    private Optional<CompanyFinancials> fetch(String symbol, String country, BigDecimal shares) {
        try {
            return "KR".equals(country)
                    ? dart.find(symbol, shares, null)
                    : edgar.find(symbol, shares, null);
        } catch (Exception e) {
            log.warn("{} 공시 재무 받기 실패: {}", symbol, e.getMessage());
            return Optional.empty();
        }
    }

    /** @return 저장했으면 true */
    private boolean save(String symbol, String country, CompanyFinancials f, LocalDateTime fetchedAt) {
        try {
            StoredFinancials row = store.findById(symbol).orElseGet(() -> new StoredFinancials(symbol, country));
            // 저장본에는 주가로 잰 값(PER·PBR)을 넣지 않는다. 읽을 때 다시 잰다
            row.update(objectMapper.writeValueAsString(f.withPrice(null)), hasHistory(f), fetchedAt);
            store.save(row);
            return true;
        } catch (Exception e) {
            // 저장이 실패해도 이번 응답은 나간다. 다음에 다시 받을 뿐이다
            log.warn("{} 공시 재무 저장 실패: {}", symbol, e.getMessage());
            return false;
        }
    }

    private CompanyFinancials read(StoredFinancials row) {
        try {
            return objectMapper.readValue(row.getFinancialsJson(), CompanyFinancials.class);
        } catch (Exception e) {
            // 저장 형식이 바뀌어 못 읽으면 없는 것으로 보고 다시 받는다
            log.warn("{} 저장된 공시 재무를 읽지 못했습니다: {}", row.getSymbol(), e.getMessage());
            return null;
        }
    }

    /** 과거 종가를 못 붙인 종목을 마지막으로 다시 시도한 때. 토스가 막혀 있으면 읽을 때마다 1초씩 헛걸음을 해서 */
    private final Map<String, LocalDateTime> historyTried = new java.util.concurrent.ConcurrentHashMap<>();
    static final Duration HISTORY_RETRY = Duration.ofHours(1);

    private boolean historyRetryDue(String symbol) {
        LocalDateTime last = historyTried.get(symbol);
        if (last != null && last.plus(HISTORY_RETRY).isAfter(LocalDateTime.now())) {
            return false;
        }
        historyTried.put(symbol, LocalDateTime.now());
        return true;
    }

    private static boolean hasHistory(CompanyFinancials f) {
        return f.history() != null && !f.history().isEmpty();
    }

    /**
     * 결산일 종가로 그 해의 PER·PBR 을 잰다.
     *
     * ★ 왜 앱이 재는가
     * 적정가 계산은 "그 회사가 과거에 받아온 PER 범위" 에 기댄다. 이 범위를 웹에서 찾으면 출처마다
     * 기준(연결/별도, 지배/연결 순이익, 결산일/연평균 주가)이 달라 같은 해 PER 이 두세 배씩 벌어진다.
     * 이익은 공시, 주가는 토스에서 같은 기준으로 가져오면 그 문제가 없다.
     *
     * 실패해도 재무는 그대로 돌려준다. 과거 배수가 없으면 예전처럼 클로드가 웹에서 찾는다.
     */
    private CompanyFinancials withHistory(String symbol, CompanyFinancials financials) {
        List<CompanyFinancials.Period> dated = financials.annual().stream()
                .filter(p -> p.periodEnd() != null)
                .toList();
        if (dated.isEmpty()) {
            return financials;
        }
        try {
            Map<LocalDate, Map.Entry<LocalDate, BigDecimal>> closes = marketData.closesOnOrBefore(
                    symbol, dated.stream().map(CompanyFinancials.Period::periodEnd).toList());
            List<CompanyFinancials.Valuation> history = new ArrayList<>();
            for (CompanyFinancials.Period period : dated) {
                Map.Entry<LocalDate, BigDecimal> close = closes.get(period.periodEnd());
                if (close != null) {
                    history.add(CompanyFinancials.Valuation.of(period, close.getKey(), close.getValue()));
                }
            }
            return financials.withHistory(history);
        } catch (Exception e) {
            log.warn("{} 과거 종가 조회 실패, 과거 PER 없이 진행합니다: {}", symbol, e.getMessage());
            return financials;
        }
    }

    /** 국내 6자리 숫자 코드면 KR, 아니면 US 로 본다. 미리보기 주소에서만 쓴다 */
    public static String guessCountry(String symbol) {
        return symbol != null && symbol.matches("\\d{6}") ? "KR" : "US";
    }
}
