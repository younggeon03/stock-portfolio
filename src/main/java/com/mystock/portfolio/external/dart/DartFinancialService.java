package com.mystock.portfolio.external.dart;

import com.mystock.portfolio.domain.DartCorpCode;
import com.mystock.portfolio.domain.DartCorpCodeRepository;
import com.mystock.portfolio.external.filing.CompanyFinancials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 국내 종목의 재무를 DART 에서 가져온다. 비율 계산은 {@link CompanyFinancials} 가 한다.
 *
 * 하는 일
 *   1. 종목코드 → DART 고유번호 (처음 한 번 매핑 파일을 받아 DB 에 넣는다)
 *   2. 가장 최근 사업보고서 → 3개년 연간 숫자
 *   3. 그다음 해의 가장 최근 분기·반기 보고서 → 올해 누적과 최근 4분기
 *
 * 재무를 못 가져와도 분석은 멈추지 않는다. 예전처럼 웹 조사로 돌아갈 뿐이다.
 */
@Service
public class DartFinancialService {

    private static final Logger log = LoggerFactory.getLogger(DartFinancialService.class);

    private static final String ANNUAL = "11011";

    /** 최근 것부터 찾는다. 3분기 보고서가 있으면 그게 가장 최신이다 */
    private static final List<String[]> INTERIM_REPORTS = List.of(
            new String[]{"11014", "3분기", "3분기보고서"},
            new String[]{"11012", "상반기", "반기보고서"},
            new String[]{"11013", "1분기", "분기보고서"});

    private final DartApiClient client;
    private final DartCorpCodeRepository corpCodes;
    private final DartProperties properties;

    public DartFinancialService(DartApiClient client, DartCorpCodeRepository corpCodes,
                                DartProperties properties) {
        this.client = client;
        this.corpCodes = corpCodes;
        this.properties = properties;
    }

    /**
     * @param shares 토스의 보통주 발행주식수. DART 에서 주식 총수를 못 받았을 때만 쓴다
     * @param price  현재가 (토스). 없으면 PER·PBR 을 비운다
     * @return 키가 없거나, DART 에 없는 종목이거나, 호출이 실패하면 비어 있다
     */
    public Optional<CompanyFinancials> find(String stockCode, BigDecimal shares, BigDecimal price) {
        if (!properties.hasKey() || stockCode == null || !stockCode.matches("\\d{6}")) {
            return Optional.empty();
        }
        try {
            Optional<DartCorpCode> corp = corpCode(stockCode);
            if (corp.isEmpty()) {
                log.info("{} DART 고유번호가 없습니다 (ETF·리츠 등은 원래 없습니다)", stockCode);
                return Optional.empty();
            }
            return Optional.ofNullable(load(corp.get(), shares, price));
        } catch (Exception e) {
            log.warn("{} DART 재무 조회 실패(웹 조사로 진행): {}", stockCode, e.getMessage());
            return Optional.empty();
        }
    }

    private CompanyFinancials load(DartCorpCode corp, BigDecimal shares, BigDecimal price) {
        // 사업보고서는 3월 말에 나온다. 작년 것이 아직 없으면 재작년 것을 쓴다
        int year = LocalDate.now().getYear() - 1;
        List<DartAccountRow> annual = client.singleAccounts(corp.getCorpCode(), year, ANNUAL);
        if (annual.isEmpty()) {
            year--;
            annual = client.singleAccounts(corp.getCorpCode(), year, ANNUAL);
        }
        if (annual.isEmpty()) {
            return null;
        }

        List<DartAccountRow> interim = List.of();
        String[] interimReport = null;
        for (String[] report : INTERIM_REPORTS) {
            interim = client.singleAccounts(corp.getCorpCode(), year + 1, report[0]);
            if (!interim.isEmpty()) {
                interimReport = report;
                break;
            }
        }

        // 주식수는 재무와 같은 보고서에서 가져온다. 기준일이 어긋나면 증자·소각이 있던 해에 EPS 가 틀린다
        BigDecimal perShareBase = shares;
        String shareBasis = shares == null ? null : "보통주 발행주식수 (토스)";
        try {
            List<DartShareRow> counts = interimReport == null
                    ? client.shareCounts(corp.getCorpCode(), year, ANNUAL)
                    : client.shareCounts(corp.getCorpCode(), year + 1, interimReport[0]);
            BigDecimal outstanding = outstandingShares(counts);
            if (outstanding != null) {
                perShareBase = outstanding;
                shareBasis = "보통주+우선주 유통주식수 (자기주식 제외, " + asOf(counts) + " 기준)";
            }
        } catch (Exception e) {
            log.warn("{} DART 주식수 조회 실패, 토스 보통주 수로 계산합니다: {}", corp.getStockCode(), e.getMessage());
        }

        // 지배주주 몫. 별도재무제표는 비지배지분이 없으니 부르지 않는다
        List<DartOwnerRow> annualOwners = List.of();
        List<DartOwnerRow> interimOwners = List.of();
        if ("CFS".equals(fsDiv(annual))) {
            try {
                annualOwners = client.fullAccounts(corp.getCorpCode(), year, ANNUAL, "CFS");
                if (interimReport != null) {
                    interimOwners = client.fullAccounts(corp.getCorpCode(), year + 1, interimReport[0], "CFS");
                }
            } catch (Exception e) {
                log.warn("{} DART 지배주주 순이익 조회 실패, 연결 순이익으로 계산합니다: {}",
                        corp.getStockCode(), e.getMessage());
            }
        }

        CompanyFinancials result = build(corp.getCorpName(), year, annual,
                interimReport == null ? null : interimReport[1],
                interimReport == null ? null : interimReport[2],
                interim, perShareBase, price, shareBasis, annualOwners, interimOwners);
        log.info("{} DART 재무: {}년 사업보고서{} ({}, 주식수 {})", corp.getStockCode(), year,
                interimReport == null ? "" : " + " + (year + 1) + "년 " + interimReport[2],
                result.statementKind(), perShareBase);
        return result;
    }

    /**
     * EPS·BPS 를 나눌 주식수. 보통주 + 우선주, 자기주식은 뺀다.
     *
     * ★ 왜 우선주를 넣는가
     * 순이익과 자본에는 우선주 몫까지 들어 있다. 보통주로만 나누면 우선주 몫까지 보통주 한 주에 얹혀서
     * EPS 가 부풀고 PER 이 낮게 나온다. 현대차는 우선주가 5,900만 주라 PER 이 20% 넘게 틀렸다.
     *
     * ★ 왜 자기주식을 빼는가
     * 회사가 사서 들고 있는 주식은 이익을 나눠 받지 않는다. 삼성전자는 8,200만 주다.
     *
     * @return 합계 줄의 유통주식수. 없으면 보통주·우선주 줄을 더하고, 그것도 없으면 null
     */
    static BigDecimal outstandingShares(List<DartShareRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        for (DartShareRow row : rows) {
            if ("합계".equals(strip(row.kind()))) {
                BigDecimal total = sharesOf(row);
                if (total != null && total.signum() > 0) {
                    return total;
                }
            }
        }
        BigDecimal sum = null;
        for (DartShareRow row : rows) {
            String kind = strip(row.kind());
            if (kind.startsWith("보통주") || kind.startsWith("우선주")) {
                BigDecimal count = sharesOf(row);
                if (count != null) {
                    sum = sum == null ? count : sum.add(count);
                }
            }
        }
        return sum != null && sum.signum() > 0 ? sum : null;
    }

    /** 유통주식수. 비어 있으면 발행 − 자기주식 */
    private static BigDecimal sharesOf(DartShareRow row) {
        BigDecimal outstanding = amount(row.outstanding());
        if (outstanding != null) {
            return outstanding;
        }
        BigDecimal issued = amount(row.issued());
        if (issued == null) {
            return null;
        }
        BigDecimal treasury = amount(row.treasury());
        return treasury == null ? issued : issued.subtract(treasury);
    }

    private static String asOf(List<DartShareRow> rows) {
        return rows.stream().map(DartShareRow::asOf).filter(d -> d != null && !d.isBlank())
                .findFirst().orElse("최근 보고서");
    }

    private static String strip(String text) {
        return text == null ? "" : text.strip();
    }

    /**
     * 공시 줄들을 기간별 숫자로 바꾼다. 호출이 없어서 테스트로 검증한다.
     *
     * @param interimLabel "상반기" 처럼 화면에 쓸 이름. 분기 보고서가 없으면 null
     */
    static CompanyFinancials build(String corpName, int annualYear, List<DartAccountRow> annualRows,
                                   String interimLabel, String interimReportName,
                                   List<DartAccountRow> interimRows, BigDecimal shares, BigDecimal price,
                                   String shareBasis) {
        return build(corpName, annualYear, annualRows, interimLabel, interimReportName, interimRows,
                shares, price, shareBasis, List.of(), List.of());
    }

    /**
     * @param annualOwners  사업보고서의 전체 재무제표 (지배주주 몫). 없으면 빈 목록
     * @param interimOwners 분기·반기 보고서의 전체 재무제표. 없으면 빈 목록
     */
    static CompanyFinancials build(String corpName, int annualYear, List<DartAccountRow> annualRows,
                                   String interimLabel, String interimReportName,
                                   List<DartAccountRow> interimRows, BigDecimal shares, BigDecimal price,
                                   String shareBasis, List<DartOwnerRow> annualOwners,
                                   List<DartOwnerRow> interimOwners) {
        // 연결을 우선한다. 자회사 실적이 빠진 별도로 PER 을 재면 지주사형 회사는 완전히 틀린다
        String fsDiv = fsDiv(annualRows);
        List<DartAccountRow> annualFs = annualRows.stream().filter(r -> fsDiv.equals(r.fsDiv())).toList();
        List<DartAccountRow> interimFs = interimRows == null ? List.of()
                : interimRows.stream().filter(r -> fsDiv.equals(r.fsDiv())).toList();

        // 사업보고서 한 건에 세 해가 들어 있다. 오래된 해부터 쌓는다
        List<CompanyFinancials.Period> annual = new ArrayList<>();
        List<Function<DartAccountRow, String>> columns = List.of(
                DartAccountRow::twoTermsAgoAmount, DartAccountRow::priorTermAmount, DartAccountRow::thisTermAmount);
        LocalDate fiscalYearEnd = fiscalYearEnd(annualFs, annualYear);
        List<Function<DartOwnerRow, String>> ownerColumns = List.of(
                DartOwnerRow::twoTermsAgoAmount, DartOwnerRow::priorTermAmount, DartOwnerRow::thisTermAmount);
        for (int i = 0; i < columns.size(); i++) {
            Raw raw = Raw.of(annualFs, columns.get(i), columns.get(i))
                    .withOwners(annualOwners, ownerColumns.get(i), ownerColumns.get(i));
            if (raw.isEmpty()) {
                continue;
            }
            annual.add(raw.toPeriod((annualYear - 2 + i) + "년", true, shares)
                    .withEnd(fiscalYearEnd.minusYears(2 - i)));
        }

        CompanyFinancials.Period interim = null;
        CompanyFinancials.Period ttm = null;
        if (!interimFs.isEmpty() && !annual.isEmpty()) {
            // 손익은 누적값(add)을 쓴다. 1분기 보고서는 누적 칸이 비어 있어 3개월 값이 곧 누적이다
            Raw ytd = Raw.of(interimFs, DartAccountRow::thisTermAmount,
                            r -> firstNonBlank(r.thisTermCumulative(), r.thisTermAmount()))
                    .withOwners(interimOwners, DartOwnerRow::thisTermAmount,
                            r -> firstNonBlank(r.thisTermCumulative(), r.thisTermAmount()));
            Raw priorYtd = Raw.of(interimFs, r -> null,
                            r -> firstNonBlank(r.priorTermCumulative(), r.priorTermAmount()))
                    .withOwners(interimOwners, r -> null,
                            r -> firstNonBlank(r.priorTermCumulative(), r.priorTermAmount()));
            interim = ytd.toPeriod((annualYear + 1) + "년 " + interimLabel + "(누적)", false, shares);

            // 재무상태는 가장 최근 시점 그대로
            Raw lastYear = Raw.of(annualFs, DartAccountRow::thisTermAmount, DartAccountRow::thisTermAmount)
                    .withOwners(annualOwners, DartOwnerRow::thisTermAmount, DartOwnerRow::thisTermAmount);
            Raw rolling = new Raw(
                    CompanyFinancials.rollForward(lastYear.revenue, ytd.revenue, priorYtd.revenue),
                    CompanyFinancials.rollForward(lastYear.operatingIncome, ytd.operatingIncome, priorYtd.operatingIncome),
                    CompanyFinancials.rollForward(lastYear.netIncome, ytd.netIncome, priorYtd.netIncome),
                    ytd.totalAssets, ytd.totalLiabilities, ytd.totalEquity,
                    CompanyFinancials.rollForward(lastYear.ownersNetIncome, ytd.ownersNetIncome, priorYtd.ownersNetIncome),
                    ytd.ownersEquity);
            ttm = rolling.toPeriod("최근 4분기 (" + (annualYear + 1) + "년 " + interimLabel + "까지)", true, shares);
        }

        List<CompanyFinancials.Source> sources = new ArrayList<>();
        addSource(sources, annualFs, annualYear + "년 사업보고서");
        if (interimReportName != null) {
            addSource(sources, interimFs, (annualYear + 1) + "년 " + interimReportName);
        }

        return CompanyFinancials.of(corpName, "DART", "KRW", "CFS".equals(fsDiv) ? "연결" : "별도",
                annual, interim, ttm, price, shareBasis, sources);
    }

    private static void addSource(List<CompanyFinancials.Source> sources, List<DartAccountRow> rows, String title) {
        rows.stream().map(DartAccountRow::receiptNo).filter(no -> no != null && !no.isBlank()).findFirst()
                .ifPresent(no -> sources.add(new CompanyFinancials.Source(
                        "DART " + title, "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=" + no)));
    }

    // ── 고유번호 ──────────────────────────────────────────

    private Optional<DartCorpCode> corpCode(String stockCode) {
        Optional<DartCorpCode> found = corpCodes.findById(stockCode);
        if (found.isPresent() || corpCodes.count() > 0) {
            return found;
        }
        refreshCorpCodes();
        return corpCodes.findById(stockCode);
    }

    /**
     * 매핑 파일을 새로 받는다. 처음 한 번은 자동으로 돈다.
     * 새로 상장한 종목이 안 잡히면 이걸 다시 부르면 된다.
     * 같은 클래스 안에서 불리므로 @Transactional 을 붙여도 안 듣는다. 지우기·넣기가 각자 트랜잭션이다.
     */
    public int refreshCorpCodes() {
        List<DartCorpCode> listed = client.listedCorpCodes();
        corpCodes.deleteAllInBatch();
        corpCodes.saveAll(listed);
        log.info("DART 고유번호 매핑 갱신: 상장사 {}건", listed.size());
        return listed.size();
    }

    // ── 공시 줄 읽기 ─────────────────────────────────────

    /** 한 기간의 원재료. 지배주주 몫은 전체 재무제표를 받았을 때만 채워진다 */
    private record Raw(BigDecimal revenue, BigDecimal operatingIncome, BigDecimal netIncome,
                       BigDecimal totalAssets, BigDecimal totalLiabilities, BigDecimal totalEquity,
                       BigDecimal ownersNetIncome, BigDecimal ownersEquity) {

        /**
         * @param balance 재무상태표(BS) 에서 읽을 칸
         * @param income  손익계산서(IS) 에서 읽을 칸
         */
        static Raw of(List<DartAccountRow> rows, Function<DartAccountRow, String> balance,
                      Function<DartAccountRow, String> income) {
            return new Raw(
                    pick(rows, "IS", n -> n.startsWith("매출액") || n.equals("수익(매출액)") || n.equals("영업수익"), income),
                    pick(rows, "IS", n -> n.startsWith("영업이익"), income),
                    pick(rows, "IS", n -> n.startsWith("당기순이익"), income),
                    pick(rows, "BS", n -> n.equals("자산총계"), balance),
                    pick(rows, "BS", n -> n.equals("부채총계"), balance),
                    pick(rows, "BS", n -> n.equals("자본총계"), balance),
                    null, null);
        }

        /** 전체 재무제표에서 지배주주 순이익·자본을 붙인다. 줄이 없으면 그대로 둔다 */
        Raw withOwners(List<DartOwnerRow> owners, Function<DartOwnerRow, String> balance,
                       Function<DartOwnerRow, String> income) {
            if (owners == null || owners.isEmpty()) {
                return this;
            }
            return new Raw(revenue, operatingIncome, netIncome, totalAssets, totalLiabilities, totalEquity,
                    ownerAmount(owners, DartOwnerRow.OWNERS_PROFIT, income),
                    ownerAmount(owners, DartOwnerRow.OWNERS_EQUITY, balance));
        }

        boolean isEmpty() {
            return revenue == null && netIncome == null && totalAssets == null && totalEquity == null;
        }

        CompanyFinancials.Period toPeriod(String label, boolean fullYear, BigDecimal shares) {
            return CompanyFinancials.Period.of(label, revenue, operatingIncome, netIncome,
                    totalAssets, totalLiabilities, totalEquity, ownersNetIncome, ownersEquity,
                    fullYear, shares, 0);
        }
    }

    /**
     * 계정 ID 로 고른다. 순이익은 손익계산서(IS) 를 먼저 보고, 없으면 포괄손익계산서(CIS).
     * 손익과 포괄손익을 한 장에 합쳐 내는 회사는 IS 가 없고 CIS 에만 있다.
     * 자본변동표(SCE) 에도 같은 ID 가 여러 번 나오는데 칸마다 뜻이 달라서 보지 않는다.
     */
    private static BigDecimal ownerAmount(List<DartOwnerRow> rows, String accountId,
                                          Function<DartOwnerRow, String> column) {
        for (String statement : List.of("BS", "IS", "CIS")) {
            for (DartOwnerRow row : rows) {
                if (statement.equals(row.sjDiv()) && accountId.equals(row.accountId())) {
                    BigDecimal value = amount(column.apply(row));
                    if (value != null) {
                        return value;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 결산일. 재무상태표 줄의 기준일("2025.12.31 현재")에서 읽는다.
     * 대부분 12월 말이지만 3월 결산 회사도 있어서 가정하지 않는다. 못 읽을 때만 12월 31일로 본다.
     */
    static LocalDate fiscalYearEnd(List<DartAccountRow> rows, int annualYear) {
        java.util.regex.Pattern date = java.util.regex.Pattern.compile("(\\d{4})\\.(\\d{2})\\.(\\d{2})");
        for (DartAccountRow row : rows) {
            if (!"BS".equals(row.sjDiv()) || row.thisTermDate() == null) {
                continue;
            }
            java.util.regex.Matcher m = date.matcher(row.thisTermDate());
            LocalDate last = null;
            while (m.find()) {
                last = LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                        Integer.parseInt(m.group(3)));
            }
            if (last != null) {
                return last;
            }
        }
        return LocalDate.of(annualYear, 12, 31);
    }

    /** 연결재무제표 줄이 하나라도 있으면 연결 */
    private static String fsDiv(List<DartAccountRow> rows) {
        return rows.stream().anyMatch(r -> "CFS".equals(r.fsDiv())) ? "CFS" : "OFS";
    }

    /** 첫 번째로 맞는 계정. 같은 이름이 두 번 나오면(당기순이익) 위쪽이 본 손익계산서다 */
    private static BigDecimal pick(List<DartAccountRow> rows, String statement, Predicate<String> name,
                                   Function<DartAccountRow, String> column) {
        return rows.stream()
                .filter(r -> statement.equals(r.sjDiv()) && r.accountName() != null && name.test(r.accountName().strip()))
                .map(column)
                .map(DartFinancialService::amount)
                .filter(v -> v != null)
                .findFirst()
                .orElse(null);
    }

    /** "1,234" → 1234. "-" 나 빈칸은 null. 음수는 "-1,234" 로 온다 */
    static BigDecimal amount(String text) {
        if (text == null) {
            return null;
        }
        String cleaned = text.replace(",", "").strip();
        if (cleaned.isEmpty() || cleaned.equals("-")) {
            return null;
        }
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }
}
