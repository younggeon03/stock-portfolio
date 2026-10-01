package com.mystock.portfolio.external.edgar;

import com.fasterxml.jackson.databind.JsonNode;
import com.mystock.portfolio.domain.SecCik;
import com.mystock.portfolio.domain.SecCikRepository;
import com.mystock.portfolio.external.filing.CompanyFinancials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 미국 종목의 재무를 SEC EDGAR 에서 가져온다. 비율 계산은 {@link CompanyFinancials} 가 한다.
 *
 * ★ DART 와 다른 점 셋
 * 1. 보고서 단위가 아니라 "사실(fact)" 단위로 온다. 한 태그에 여러 공시의 여러 기간이 섞여 있어서
 *    기간(시작일·종료일)으로 직접 골라야 한다.
 * 2. 태그 이름이 회사마다, 해마다 바뀐다. 브로드컴은 순이익이 2024년까지 NetIncomeLoss, 그 뒤로 ProfitLoss 다.
 *    그래서 후보를 여러 개 두고 **가장 최근 기간까지 있는 태그**를 쓴다.
 * 3. 회계연도가 달력과 다르다. 브로드컴은 11월 초, 엔비디아는 1월 말에 끝난다.
 *    그래서 "2025년" 이 아니라 결산일로 기간을 가른다.
 */
@Service
public class EdgarFinancialService {

    private static final Logger log = LoggerFactory.getLogger(EdgarFinancialService.class);

    /** 앞에 있을수록 우선. 최근 기간까지 있는 태그가 없을 때만 순서가 의미 있다 */
    static final List<String> REVENUE = List.of(
            "RevenueFromContractWithCustomerExcludingAssessedTax", "Revenues", "SalesRevenueNet",
            "RevenueFromContractWithCustomerIncludingAssessedTax");
    static final List<String> OPERATING_INCOME = List.of("OperatingIncomeLoss");
    /** 지배주주 몫(NetIncomeLoss)을 앞에 둔다. 없을 때만 비지배 포함(ProfitLoss) */
    static final List<String> NET_INCOME = List.of(
            "NetIncomeLoss", "ProfitLoss", "NetIncomeLossAvailableToCommonStockholdersBasic");
    static final List<String> ASSETS = List.of("Assets");
    static final List<String> LIABILITIES = List.of("Liabilities");
    static final List<String> LIABILITIES_AND_EQUITY = List.of("LiabilitiesAndStockholdersEquity");
    static final List<String> EQUITY = List.of(
            "StockholdersEquity", "StockholdersEquityIncludingPortionAttributableToNoncontrollingInterest");

    private static final Set<String> FORMS = Set.of("10-K", "10-K/A", "10-Q", "10-Q/A");

    private final EdgarApiClient client;
    private final SecCikRepository ciks;
    private final EdgarProperties properties;

    public EdgarFinancialService(EdgarApiClient client, SecCikRepository ciks, EdgarProperties properties) {
        this.client = client;
        this.ciks = ciks;
        this.properties = properties;
    }

    /**
     * @param shares 지금 발행주식수 (토스). 없으면 SEC 표지에 적힌 주식수를 쓴다
     * @param price  현재가 (토스, 달러). 없으면 PER·PBR 을 비운다
     */
    public Optional<CompanyFinancials> find(String ticker, BigDecimal shares, BigDecimal price) {
        if (!properties.hasUserAgent() || ticker == null || ticker.isBlank()) {
            return Optional.empty();
        }
        String secTicker = ticker.trim().toUpperCase().replace('.', '-');
        try {
            Optional<SecCik> cik = cik(secTicker);
            if (cik.isEmpty()) {
                log.info("{} SEC CIK 가 없습니다 (ETF 는 원래 재무가 없습니다)", ticker);
                return Optional.empty();
            }
            JsonNode facts = client.companyFacts(cik.get().getCik());
            CompanyFinancials result = build(facts, cik.get().getCik(), shares, price);
            if (result == null) {
                log.info("{} EDGAR 에 쓸 만한 연간 재무가 없습니다", ticker);
                return Optional.empty();
            }
            log.info("{} EDGAR 재무: 연간 {}개{}", ticker, result.annual().size(),
                    result.interim() == null ? "" : " + " + result.interim().label());
            return Optional.of(result);
        } catch (Exception e) {
            log.warn("{} EDGAR 재무 조회 실패(웹 조사로 진행): {}", ticker, e.getMessage());
            return Optional.empty();
        }
    }

    // ── 해석 ────────────────────────────────────────────

    /** companyfacts 한 덩어리를 기간별 숫자로 바꾼다. 호출이 없어서 테스트로 검증한다 */
    static CompanyFinancials build(JsonNode root, long cik, BigDecimal shares, BigDecimal price) {
        JsonNode gaap = root.path("facts").path("us-gaap");
        if (gaap.isMissingNode()) {
            return null;
        }
        Series revenue = Series.pick(gaap, REVENUE, "USD");
        Series operating = Series.pick(gaap, OPERATING_INCOME, "USD");
        Series net = Series.pick(gaap, NET_INCOME, "USD");
        Series assets = Series.pick(gaap, ASSETS, "USD");
        Series liabilities = Series.pick(gaap, LIABILITIES, "USD");
        Series liabilitiesAndEquity = Series.pick(gaap, LIABILITIES_AND_EQUITY, "USD");
        Series equity = Series.pick(gaap, EQUITY, "USD");

        // 연간 결산일은 순이익 기준으로 잡는다. 매출이 없는 회사(금융지주)는 있어도 순이익이 없는 회사는 없다
        Series anchor = net.isEmpty() ? revenue : net;
        List<Fact> annualFacts = anchor.annualFacts();
        if (annualFacts.isEmpty()) {
            return null;
        }
        List<Fact> lastThree = annualFacts.subList(Math.max(0, annualFacts.size() - 3), annualFacts.size());

        boolean fromToss = shares != null && shares.signum() > 0;
        BigDecimal perShareBase = fromToss ? shares : coverShares(root);
        String shareBasis = fromToss ? "보통주 발행주식수 (토스)" : "보통주 발행주식수 (SEC 공시 표지)";

        List<CompanyFinancials.Period> annual = new ArrayList<>();
        for (Fact year : lastThree) {
            LocalDate end = year.end();
            BigDecimal eq = equity.instant(end);
            annual.add(CompanyFinancials.Period.of(
                    "FY" + end.getYear() + " (" + end + " 결산)",
                    revenue.flow(year.start(), end), operating.flow(year.start(), end), net.flow(year.start(), end),
                    assets.instant(end), liabilities(liabilities, liabilitiesAndEquity, eq, end), eq,
                    true, perShareBase, 2).withEnd(end));
        }

        Fact lastYear = lastThree.get(lastThree.size() - 1);
        CompanyFinancials.Period interim = null;
        CompanyFinancials.Period ttm = null;
        Fact ytd = anchor.latestYtdAfter(lastYear.end());
        if (ytd != null) {
            LocalDate end = ytd.end();
            String quarter = ytd.fp() == null ? "" : " " + ytd.fp();
            int fiscalYear = ytd.fy() != null ? ytd.fy() : end.getYear();
            String label = "FY" + fiscalYear + quarter + " 누적 (~" + end + ")";
            BigDecimal eq = equity.instant(end);
            BigDecimal assetsNow = assets.instant(end);
            BigDecimal liabilitiesNow = liabilities(liabilities, liabilitiesAndEquity, eq, end);

            BigDecimal revYtd = revenue.flow(ytd.start(), end);
            BigDecimal opYtd = operating.flow(ytd.start(), end);
            BigDecimal netYtd = net.flow(ytd.start(), end);
            interim = CompanyFinancials.Period.of(label, revYtd, opYtd, netYtd,
                    assetsNow, liabilitiesNow, eq, false, perShareBase, 2);

            // 작년 같은 기간 누적. 10-Q 에 비교 칸으로 같이 실려 있다
            long days = ChronoUnit.DAYS.between(ytd.start(), end);
            LocalDate yearAgo = end.minusDays(364);
            ttm = CompanyFinancials.Period.of("최근 4분기 (~" + end + ")",
                    CompanyFinancials.rollForward(revenue.flow(lastYear.start(), lastYear.end()), revYtd,
                            revenue.flowNear(yearAgo, days)),
                    CompanyFinancials.rollForward(operating.flow(lastYear.start(), lastYear.end()), opYtd,
                            operating.flowNear(yearAgo, days)),
                    CompanyFinancials.rollForward(net.flow(lastYear.start(), lastYear.end()), netYtd,
                            net.flowNear(yearAgo, days)),
                    assetsNow, liabilitiesNow, eq, true, perShareBase, 2);
        }

        List<CompanyFinancials.Source> sources = new ArrayList<>();
        sources.add(source(cik, lastYear.accn(), "SEC 10-K FY" + lastYear.end().getYear()));
        if (ytd != null) {
            sources.add(source(cik, ytd.accn(), "SEC 10-Q FY" + (ytd.fy() != null ? ytd.fy() : ytd.end().getYear()) + (ytd.fp() == null ? "" : " " + ytd.fp())));
        }

        String name = root.path("entityName").asText("");
        return CompanyFinancials.of(name, "SEC EDGAR", "USD", "연결",
                annual, interim, ttm, price, shareBasis, sources);
    }

    /** 부채 태그가 없는 회사가 있다. 그때는 부채와자본총계 − 자본 */
    private static BigDecimal liabilities(Series liabilities, Series liabilitiesAndEquity, BigDecimal equity,
                                          LocalDate end) {
        BigDecimal direct = liabilities.instant(end);
        if (direct != null) {
            return direct;
        }
        BigDecimal total = liabilitiesAndEquity.instant(end);
        return total == null || equity == null ? null : total.subtract(equity);
    }

    /** 10-K·10-Q 표지에 적힌 발행주식수. 토스가 막혔을 때 쓴다 */
    private static BigDecimal coverShares(JsonNode root) {
        JsonNode rows = root.path("facts").path("dei").path("EntityCommonStockSharesOutstanding")
                .path("units").path("shares");
        BigDecimal latest = null;
        String latestEnd = "";
        for (JsonNode row : rows) {
            String end = row.path("end").asText("");
            if (end.compareTo(latestEnd) >= 0) {
                latestEnd = end;
                latest = row.path("val").decimalValue();
            }
        }
        return latest;
    }

    private static CompanyFinancials.Source source(long cik, String accn, String title) {
        return new CompanyFinancials.Source(title,
                "https://www.sec.gov/Archives/edgar/data/" + cik + "/" + accn.replace("-", "") + "/");
    }

    /** 공시 숫자 하나. start 가 null 이면 시점 값(재무상태), 있으면 기간 값(손익) */
    record Fact(LocalDate start, LocalDate end, BigDecimal val, Integer fy, String fp, String form,
                String filed, String accn) {

        long days() {
            return start == null ? 0 : ChronoUnit.DAYS.between(start, end);
        }

        boolean isAnnual() {
            long d = days();
            return d >= 350 && d <= 380;
        }
    }

    /** 한 태그의 사실 목록. 10-K·10-Q 에서 온 것만 */
    record Series(String tag, List<Fact> facts) {

        boolean isEmpty() {
            return facts.isEmpty();
        }

        /** 후보 태그 중 가장 최근 기간까지 있는 것. 같으면 앞의 것 */
        static Series pick(JsonNode gaap, List<String> tags, String unit) {
            Series best = new Series(null, List.of());
            LocalDate bestEnd = LocalDate.MIN;
            for (String tag : tags) {
                List<Fact> facts = read(gaap.path(tag).path("units").path(unit));
                LocalDate latest = facts.stream().map(Fact::end).max(Comparator.naturalOrder()).orElse(null);
                if (latest != null && latest.isAfter(bestEnd)) {
                    best = new Series(tag, facts);
                    bestEnd = latest;
                }
            }
            return best;
        }

        private static List<Fact> read(JsonNode rows) {
            List<Fact> facts = new ArrayList<>();
            for (JsonNode r : rows) {
                if (!FORMS.contains(r.path("form").asText())) {
                    continue;
                }
                facts.add(new Fact(
                        r.hasNonNull("start") ? LocalDate.parse(r.get("start").asText()) : null,
                        LocalDate.parse(r.path("end").asText()),
                        r.path("val").decimalValue(),
                        r.hasNonNull("fy") ? r.get("fy").asInt() : null,
                        r.hasNonNull("fp") ? r.get("fp").asText() : null,
                        r.path("form").asText(),
                        r.path("filed").asText(""),
                        r.path("accn").asText("")));
            }
            return facts;
        }

        /**
         * 결산일별 1년치 값. 오래된 순.
         * 같은 기간이 여러 10-K 에 비교 칸으로 반복 실리므로 가장 늦게 낸 것(정정 반영)을 남긴다.
         */
        List<Fact> annualFacts() {
            Map<LocalDate, Fact> byEnd = new LinkedHashMap<>();
            facts.stream()
                    .filter(f -> f.isAnnual() && f.form().startsWith("10-K"))
                    .sorted(Comparator.comparing(Fact::filed))
                    .forEach(f -> byEnd.put(f.end(), f));
            return byEnd.values().stream().sorted(Comparator.comparing(Fact::end)).toList();
        }

        /** 정확히 그 기간의 값. 여러 번 실렸으면 가장 늦게 낸 것 */
        BigDecimal flow(LocalDate start, LocalDate end) {
            if (start == null) {
                return null;
            }
            return facts.stream()
                    .filter(f -> end.equals(f.end()) && start.equals(f.start()))
                    .max(Comparator.comparing(Fact::filed))
                    .map(Fact::val)
                    .orElse(null);
        }

        /**
         * 종료일과 길이가 대략 맞는 기간 값. 작년 같은 기간 누적을 찾을 때 쓴다.
         * 회계연도가 52·53주라 날짜가 며칠씩 어긋나서 정확히 맞출 수 없다.
         */
        BigDecimal flowNear(LocalDate end, long days) {
            return facts.stream()
                    .filter(f -> f.start() != null
                            && Math.abs(ChronoUnit.DAYS.between(end, f.end())) <= 10
                            && Math.abs(f.days() - days) <= 10)
                    .max(Comparator.comparing(Fact::filed))
                    .map(Fact::val)
                    .orElse(null);
        }

        /** 그 시점의 값 (재무상태) */
        BigDecimal instant(LocalDate end) {
            return facts.stream()
                    .filter(f -> f.start() == null && end.equals(f.end()))
                    .max(Comparator.comparing(Fact::filed))
                    .map(Fact::val)
                    .orElse(null);
        }

        /**
         * 결산 뒤 가장 최근 10-Q 의 연초부터 누적 값.
         * 3개월치가 아니라 누적이어야 한다. 시작일이 결산 다음 날 근처인 것 중 가장 늦게 끝나는 것.
         */
        Fact latestYtdAfter(LocalDate fiscalYearEnd) {
            return facts.stream()
                    .filter(f -> f.form().startsWith("10-Q") && f.start() != null
                            && f.end().isAfter(fiscalYearEnd)
                            && Math.abs(ChronoUnit.DAYS.between(fiscalYearEnd, f.start())) <= 10)
                    .max(Comparator.comparing(Fact::end).thenComparing(Fact::filed))
                    .orElse(null);
        }
    }

    // ── CIK ─────────────────────────────────────────────

    private Optional<SecCik> cik(String ticker) {
        Optional<SecCik> found = ciks.findById(ticker);
        if (found.isPresent() || ciks.count() > 0) {
            return found;
        }
        refreshCiks();
        return ciks.findById(ticker);
    }

    /** 매핑 파일을 새로 받는다. 처음 한 번은 자동으로 돈다 */
    public int refreshCiks() {
        List<SecCik> all = client.tickers();
        ciks.deleteAllInBatch();
        ciks.saveAll(all);
        log.info("SEC CIK 매핑 갱신: {}건", all.size());
        return all.size();
    }
}
