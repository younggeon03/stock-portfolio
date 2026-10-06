package com.mystock.portfolio.external.filing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * 공시(DART·SEC EDGAR)에서 가져온 재무 숫자와, 그걸로 앱이 계산한 비율.
 *
 * ★ 비율까지 앱이 계산하는 이유
 * 부채비율·ROE·PER·PBR 은 공시 숫자 두 개를 나누면 끝나는 값이다.
 * 이걸 웹에서 긁으면 기준일·기준(연결/별도)이 제각각인 숫자가 섞인다. 결정기록 003 과 같은 이유다.
 *
 * ★ 한국과 미국이 같은 모양인 이유
 * 프롬프트와 화면은 어디서 왔는지 몰라도 된다. 증권사 어댑터와 같은 생각이다.
 * 공시처마다 다른 건 "원재료 숫자를 어떻게 꺼내나" 뿐이고, 나누기는 여기 한 곳에서 한다.
 *
 * @param corpName      공시처에 등록된 회사명
 * @param filer         "DART" 또는 "SEC EDGAR"
 * @param currency      KRW 또는 USD. 금액과 주당 지표의 단위
 * @param statementKind "연결" 또는 "별도"
 * @param annual        연간 숫자. 오래된 해부터
 * @param interim       올해(회계연도) 가장 최근 분기까지 누적. 아직 없으면 null
 * @param ttm           최근 4분기 합산 (interim 이 있을 때만). PER 은 이걸로 계산한다
 * @param per           현재가 ÷ 최근 4분기 주당순이익. 적자면 null
 * @param pbr           현재가 ÷ 최근 주당순자산
 * @param shareBasis    주당 지표를 무엇으로 나눴는지. 예: "보통주+우선주 유통주식수 (자기주식 제외, 2026-06-30)"
 *                      우선주가 있는 회사는 이 기준에 따라 PER 이 20% 넘게 달라져서 프롬프트에 같이 밝힌다
 * @param history       결산일마다 그때 주가로 잰 PER·PBR. 그 회사가 받아온 배수 범위다. 주가를 못 구하면 비어 있다
 * @param sources       근거가 된 공시 원문 주소
 */
public record CompanyFinancials(
        String corpName,
        String filer,
        String currency,
        String statementKind,
        List<Period> annual,
        Period interim,
        Period ttm,
        BigDecimal per,
        BigDecimal pbr,
        String shareBasis,
        List<Valuation> history,
        List<Source> sources
) {

    /**
     * 결산일 한 번의 배수.
     *
     * @param label     연간 기간 이름. 예: "2024년", "FY2025 (2025-11-02 결산)"
     * @param tradeDate 종가를 가져온 거래일. 결산일이 휴장이면 그 직전
     * @param close     그날 종가
     * @param per       종가 ÷ 그 해 EPS. 적자면 null
     * @param pbr       종가 ÷ 그 해 말 BPS
     */
    public record Valuation(String label, LocalDate tradeDate, BigDecimal close, BigDecimal per, BigDecimal pbr) {

        public static Valuation of(Period period, LocalDate tradeDate, BigDecimal close) {
            BigDecimal per = period.eps() == null || period.eps().signum() <= 0 ? null : divide(close, period.eps(), 2);
            BigDecimal pbr = period.bps() == null || period.bps().signum() <= 0 ? null : divide(close, period.bps(), 2);
            return new Valuation(period.label(), tradeDate, close, per, pbr);
        }
    }

    /** 과거 배수를 붙인 사본. 주가는 공시처가 아니라 토스에서 오므로 나중에 붙인다 */
    public CompanyFinancials withHistory(List<Valuation> history) {
        return new CompanyFinancials(corpName, filer, currency, statementKind, annual, interim, ttm,
                per, pbr, shareBasis, List.copyOf(history), sources);
    }

    /**
     * 기간들을 받아 PER·PBR 을 계산해 묶는다.
     * PER 은 최근 4분기, 없으면 최근 연간. PBR 은 가장 최근 재무상태.
     */
    public static CompanyFinancials of(String corpName, String filer, String currency, String statementKind,
                                       List<Period> annual, Period interim, Period ttm,
                                       BigDecimal price, String shareBasis, List<Source> sources) {
        return new CompanyFinancials(corpName, filer, currency, statementKind,
                annual, interim, ttm, null, null, shareBasis, List.of(), sources).withPrice(price);
    }

    /**
     * 지금 주가로 PER·PBR 을 다시 잰 사본. 공시 재무는 DB 에 주가 없이 저장해 두고(분기에 한 번 바뀜),
     * 주가에 따라 매일 바뀌는 배수는 읽을 때마다 여기서 계산한다.
     * PER 은 최근 4분기, 없으면 최근 연간. PBR 은 가장 최근 재무상태. price 가 null 이면 둘 다 빈다
     */
    public CompanyFinancials withPrice(BigDecimal price) {
        List<Period> years = annual == null ? List.of() : annual;
        Period earningsBase = ttm != null ? ttm : years.isEmpty() ? null : years.get(years.size() - 1);
        Period bookBase = interim != null ? interim : earningsBase;
        BigDecimal newPer = earningsBase == null || earningsBase.eps() == null || earningsBase.eps().signum() <= 0
                ? null : divide(price, earningsBase.eps(), 2);
        BigDecimal newPbr = bookBase == null || bookBase.bps() == null || bookBase.bps().signum() <= 0
                ? null : divide(price, bookBase.bps(), 2);
        return new CompanyFinancials(corpName, filer, currency, statementKind,
                annual, interim, ttm, newPer, newPbr, shareBasis, history == null ? List.of() : history, sources);
    }

    /**
     * 한 기간의 숫자. 금액은 원 또는 달러.
     *
     * @param label           "2025년", "2026년 상반기(누적)", "최근 4분기" 같은 화면용 이름
     * @param netIncome       연결 순이익. 비지배지분 몫까지 포함
     * @param totalEquity     자본총계. 비지배지분 포함
     * @param ownersNetIncome 지배주주 순이익. 모르면 null (그때는 주당 지표를 연결 순이익으로 잰다)
     * @param ownersEquity    지배주주 자본. 모르면 null
     * @param debtRatio       부채 ÷ 자본총계 × 100. 부채비율은 관례상 자본총계로 나눈다
     * @param roe             지배주주 순이익 ÷ 지배주주 자본 × 100. 누적 중인 분기는 1년치가 아니라서 비운다
     * @param eps             지배주주 순이익 ÷ 주식수. 과거 주식수가 아니라 지금 주식수라는 한계가 있다
     * @param bps             지배주주 자본 ÷ 주식수
     * @param periodEnd       기간 마지막 날(결산일). 과거 PER 을 잴 종가 날짜다. 모르면 null
     */
    public record Period(
            String label,
            BigDecimal revenue,
            BigDecimal operatingIncome,
            BigDecimal netIncome,
            BigDecimal totalAssets,
            BigDecimal totalLiabilities,
            BigDecimal totalEquity,
            BigDecimal ownersNetIncome,
            BigDecimal ownersEquity,
            BigDecimal debtRatio,
            BigDecimal operatingMargin,
            BigDecimal roe,
            BigDecimal eps,
            BigDecimal bps,
            LocalDate periodEnd
    ) {

        /** 결산일을 붙인 사본 */
        public Period withEnd(LocalDate end) {
            return new Period(label, revenue, operatingIncome, netIncome, totalAssets, totalLiabilities,
                    totalEquity, ownersNetIncome, ownersEquity, debtRatio, operatingMargin, roe, eps, bps, end);
        }

        /** 지배주주 몫을 모를 때. 주당 지표를 연결 순이익·자본총계로 잰다 */
        public static Period of(String label, BigDecimal revenue, BigDecimal operatingIncome, BigDecimal netIncome,
                                BigDecimal totalAssets, BigDecimal totalLiabilities, BigDecimal totalEquity,
                                boolean fullYear, BigDecimal shares, int perShareScale) {
            return of(label, revenue, operatingIncome, netIncome, totalAssets, totalLiabilities, totalEquity,
                    null, null, fullYear, shares, perShareScale);
        }

        /**
         * 원재료 숫자로 비율을 계산한다.
         *
         * ★ 주당 지표는 지배주주 몫으로 잰다
         * 주가는 지배기업 주식의 값이다. 자회사 소수주주(비지배지분) 몫까지 넣으면 EPS 가 부풀어 PER 이 낮게 나온다.
         * 현대차는 2025년 연결 순이익 10.36조 중 0.92조가 비지배지분이다.
         *
         * @param fullYear 1년치 손익인가. 아니면 ROE·EPS 를 비운다 (반기 이익으로 재면 반토막이 난다)
         * @param perShareScale 주당 지표 소수 자릿수. 원은 0, 달러는 2
         */
        public static Period of(String label, BigDecimal revenue, BigDecimal operatingIncome, BigDecimal netIncome,
                                BigDecimal totalAssets, BigDecimal totalLiabilities, BigDecimal totalEquity,
                                BigDecimal ownersNetIncome, BigDecimal ownersEquity,
                                boolean fullYear, BigDecimal shares, int perShareScale) {
            boolean hasShares = shares != null && shares.signum() > 0;
            BigDecimal earnings = ownersNetIncome != null ? ownersNetIncome : netIncome;
            BigDecimal book = ownersEquity != null ? ownersEquity : totalEquity;
            return new Period(label, revenue, operatingIncome, netIncome,
                    totalAssets, totalLiabilities, totalEquity, ownersNetIncome, ownersEquity,
                    percent(totalLiabilities, totalEquity),
                    percent(operatingIncome, revenue),
                    fullYear ? percent(earnings, book) : null,
                    hasShares && fullYear ? divide(earnings, shares, perShareScale) : null,
                    hasShares ? divide(book, shares, perShareScale) : null, null);
        }
    }

    /** 공시 원문. 모델이 sources 에 그대로 옮겨 적을 수 있게 주소까지 만든다 */
    public record Source(String title, String url) {
    }

    /** 최근 4분기 = 작년 연간 + 올해 누적 − 작년 같은 기간 누적 */
    public static BigDecimal rollForward(BigDecimal annual, BigDecimal ytd, BigDecimal priorYtd) {
        if (annual == null || ytd == null || priorYtd == null) {
            return null;
        }
        return annual.add(ytd).subtract(priorYtd);
    }

    private static BigDecimal percent(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.signum() == 0) {
            return null;
        }
        return numerator.multiply(BigDecimal.valueOf(100)).divide(denominator, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal divide(BigDecimal numerator, BigDecimal denominator, int scale) {
        if (numerator == null || denominator == null || denominator.signum() == 0) {
            return null;
        }
        return numerator.divide(denominator, scale, RoundingMode.HALF_UP);
    }
}
