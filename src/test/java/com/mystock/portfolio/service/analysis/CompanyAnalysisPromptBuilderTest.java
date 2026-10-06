package com.mystock.portfolio.service.analysis;

import com.mystock.portfolio.external.filing.CompanyFinancials;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 프롬프트 조립 테스트.
 *
 * ★ 왜 이걸 테스트하는가
 * 이 기능의 신뢰도는 "앱이 아는 숫자를 클로드가 그대로 쓰는가" 에 달려 있다.
 * 평단가나 비중이 프롬프트에서 누락되면 클로드가 스스로 지어내고, 그러면 분석 전체를 믿을 수 없다.
 * 그래서 확정 정보가 실제로 프롬프트에 들어가는지를 테스트로 고정한다.
 */
class CompanyAnalysisPromptBuilderTest {

    private final CompanyAnalysisPromptBuilder builder = new CompanyAnalysisPromptBuilder();

    @Test
    void 보유현황_숫자가_프롬프트에_그대로_들어간다() {
        String prompt = builder.userPrompt(soxl());

        // 평단가·현재가·비중은 분석의 핵심 근거다. 하나라도 빠지면 안 된다.
        assertThat(prompt).contains("151.82");        // 평단가
        assertThat(prompt).contains("122.25");        // 현재가
        assertThat(prompt).contains("81.47");         // 비중
        assertThat(prompt).contains("16,381,500");    // 평가금액 (천 단위 구분)
        assertThat(prompt).contains("나무증권 + 토스증권");
    }

    @Test
    void 확정정보라는_점을_분명히_알린다() {
        String prompt = builder.userPrompt(soxl());

        // 이 문구가 빠지면 클로드가 숫자를 다시 계산하려 든다
        assertThat(prompt).contains("확정값");
        assertThat(prompt).contains("바꾸지 마라");
    }

    @Test
    void 손실은_마이너스로_수익은_플러스로_표시된다() {
        String prompt = builder.userPrompt(soxl());

        // 손익 -3,962,139원 / 수익률 -19.47%
        assertThat(prompt).contains("-3,962,139");
        assertThat(prompt).contains("-19.47");
    }

    @Test
    void 공시_재무가_있으면_표와_출처를_넣고_재무_검색을_막는다() {
        CompanyFinancials.Period y2025 = new CompanyFinancials.Period("2025년",
                new BigDecimal("333605938000000"), new BigDecimal("43601051000000"), new BigDecimal("45206805000000"),
                new BigDecimal("566942110000000"), new BigDecimal("130621773000000"), new BigDecimal("436320337000000"),
                null, null,
                new BigDecimal("29.94"), new BigDecimal("13.07"), new BigDecimal("10.36"),
                new BigDecimal("7637"), new BigDecimal("73707"), LocalDate.of(2025, 12, 31));
        CompanyFinancials financials = new CompanyFinancials("삼성전자", "DART", "KRW", "연결",
                List.of(y2025), null, null,
                new BigDecimal("13.09"), new BigDecimal("1.36"),
                "보통주+우선주 유통주식수 (자기주식 제외, 2026-06-30 기준)",
                List.of(CompanyFinancials.Valuation.of(y2025, LocalDate.of(2025, 12, 30), new BigDecimal("53000"))),
                List.of(new CompanyFinancials.Source("DART 2025년 사업보고서",
                        "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=20260310000001")));

        CompanyAnalysisFacts base = hyundai();
        CompanyAnalysisFacts facts = new CompanyAnalysisFacts(
                base.symbol(), base.name(), base.englishName(), base.marketCountry(), base.market(),
                base.currency(), base.securityType(), base.leverageFactor(), base.sharesOutstanding(),
                true, base.quantity(), base.lastPrice(), base.averagePurchasePrice(), base.priceGapPercent(),
                base.marketValueKrw(), base.purchaseKrw(), base.profitLossKrw(), base.profitRatePercent(),
                base.weightPercent(), base.totalValueKrw(), base.brokers(),
                base.annualizedVolatilityPercent(), base.dailyVolatilityPercent(), base.periodReturnPercent(),
                base.dataPoints(), base.periodHigh(), base.periodLow(), base.riskFlags(), financials, base.asOf());

        String prompt = builder.userPrompt(facts);

        assertThat(prompt).contains("[공시 재무]");
        assertThat(prompt).contains("3,336,059");      // 매출 억원
        assertThat(prompt).contains("PER 13.09배");
        assertThat(prompt).contains("rcpNo=20260310000001");
        // 우선주를 넣었는지에 따라 PER 이 20% 넘게 달라진다. 무엇으로 나눴는지 모델도 알아야 한다
        assertThat(prompt).contains("보통주+우선주 유통주식수");
        // 과거 배수가 있으면 표로 넣고, 웹에서 PER 범위를 찾지 말라고 한다 (53,000 ÷ 7,637 = 6.94배)
        assertThat(prompt).contains("[과거 배수]");
        assertThat(prompt).contains("| 2025년 | 2025-12-30 | 53,000원 | 6.94배 |");
        assertThat(prompt).contains("적정가의 PER 범위는 이 표에서 잡아라");
        // 재무가 이미 있으니 재무제표 검색에 횟수를 쓰지 말라고 해야 한다
        assertThat(prompt).containsPattern("재무제표[^\\n]* 는 아래 자료에 있으니 검색하지 마라");
        assertThat(prompt).doesNotContain("사업보고서 재무제표\"");
    }

    @Test
    void 미국_공시_재무는_백만_달러와_센트로_넣는다() {
        CompanyFinancials.Period fy2025 = CompanyFinancials.Period.of("FY2025 (2025-11-02 결산)",
                new BigDecimal("63887000000"), new BigDecimal("25484000000"), new BigDecimal("23126000000"),
                new BigDecimal("171092000000"), new BigDecimal("89800000000"), new BigDecimal("81292000000"),
                true, new BigDecimal("4773629865"), 2);
        CompanyFinancials financials = CompanyFinancials.of("Broadcom Inc.", "SEC EDGAR", "USD", "연결",
                List.of(fy2025), null, null, new BigDecimal("363.5"), "보통주 발행주식수 (토스)",
                List.of(new CompanyFinancials.Source("SEC 10-K FY2025",
                        "https://www.sec.gov/Archives/edgar/data/1730168/000173016825000121/")));

        CompanyAnalysisFacts base = soxl();
        CompanyAnalysisFacts facts = new CompanyAnalysisFacts(
                "AVGO", "브로드컴", "Broadcom Inc.", "US", "NASDAQ", "USD", "STOCK", null,
                new BigDecimal("4773629865"),
                true, base.quantity(), new BigDecimal("363.5"), base.averagePurchasePrice(), base.priceGapPercent(),
                base.marketValueKrw(), base.purchaseKrw(), base.profitLossKrw(), base.profitRatePercent(),
                base.weightPercent(), base.totalValueKrw(), base.brokers(),
                null, null, null, null, null, null, List.of(), financials, base.asOf());

        String prompt = builder.userPrompt(facts);

        assertThat(prompt).contains("SEC EDGAR 에 공시된");
        assertThat(prompt).contains("백만 달러");
        assertThat(prompt).contains("| 63,887 |");   // 매출 백만 달러
        assertThat(prompt).contains("| 4.84 |");       // EPS 달러 (23,126M / 4,773M주)
        assertThat(prompt).contains("현재가 $363.50 기준");
        assertThat(prompt).contains("000173016825000121");
        // 한국 연결 순이익 경고는 미국 종목에 붙지 않는다
        assertThat(prompt).doesNotContain("비지배지분을 포함한 연결 당기순이익");
        assertThat(prompt).containsPattern("재무제표[^\\n]* 는 아래 자료에 있으니 검색하지 마라");
    }

    @Test
    void 레버리지_상품이면_프롬프트에_명시한다() {
        String prompt = builder.userPrompt(soxl());
        assertThat(prompt).contains("레버리지 3배 상품이다");
    }

    @Test
    void 위험신호가_프롬프트에_포함된다() {
        String prompt = builder.userPrompt(soxl());

        assertThat(prompt).contains("[위험 신호]");
        assertThat(prompt).contains("전체 자산의 81.47% 를 차지한다");
        assertThat(prompt).contains("risks 에 반드시 반영");
    }

    @Test
    void 국내종목은_DART_를_미국종목은_SEC_를_안내한다() {
        assertThat(builder.userPrompt(hyundai())).contains("DART");
        assertThat(builder.userPrompt(hyundai())).contains("005380");

        assertThat(builder.userPrompt(soxl())).contains("SEC EDGAR");
        assertThat(builder.userPrompt(soxl())).doesNotContain("DART");
    }

    @Test
    void 국내종목은_이름과_코드가_맞는지_확인하라고_경고한다() {
        // 454910 처럼 생소한 코드는 엉뚱한 회사로 착각할 수 있다
        assertThat(builder.userPrompt(hyundai())).contains("종목명과 코드가 모두 일치하는 회사인지");
    }

    @Test
    void 평단가를_모르면_평단가_줄을_아예_넣지_않는다() {
        // 0 으로 채워 넣으면 "공짜로 샀다" 는 거짓 정보가 된다. 아예 빼는 게 맞다.
        CompanyAnalysisFacts noAverage = withoutAveragePrice();
        String prompt = builder.userPrompt(noAverage);

        assertThat(prompt).doesNotContain("내 평단가");
        assertThat(prompt).doesNotContain("평단가 대비 현재가");
    }

    @Test
    void 시세통계가_없어도_프롬프트가_만들어진다() {
        // 토스 호출 한도에 걸려 변동성을 못 구해도 분석은 진행되어야 한다
        String prompt = builder.userPrompt(withoutMarketStats());

        assertThat(prompt).doesNotContain("[시세 통계]");
        assertThat(prompt).contains("[보유현황]");
        assertThat(prompt).contains("submit_analysis");
    }

    @Test
    void 시스템프롬프트에_핵심규칙이_들어있다() {
        String system = builder.systemPrompt();

        assertThat(system).contains("지어내지 마라");
        assertThat(system).contains("submit_analysis");
        // 불리한 사실을 돌려 말하지 않게 하는 규칙이 이 기능의 존재 이유다
        assertThat(system).contains("부드럽게 돌려 말하지 마라");
    }

    @Test
    void 판정은_한_곳에서만_내리게_묶여있다() {
        // 판정을 넣기로 했지만 아무 데서나 매수·매도를 말하면 안 된다.
        // 섹션은 사실만 적고 판단은 verdict 한 곳에서만 나온다.
        String system = builder.systemPrompt();

        assertThat(system).contains("verdict");
        assertThat(system).contains("판정은 `verdict` 한 곳에서만 내린다");
        assertThat(system).contains("목표주가를 제시하지 마라");
    }

    @Test
    void 애매하게_끝내지_못하게_막아뒀다() {
        // "지켜볼 필요가 있다" 같은 문장은 아무 정보도 없으면서 뭔가 말한 것처럼 보인다.
        // 이런 문장이 섞이면 분석 전체가 안 읽히게 된다.
        String system = builder.systemPrompt();

        assertThat(system).contains("애매하게 끝내지 마라");
        assertThat(system).contains("지켜볼 필요가 있다");
        // 모르는 것을 모른다고 쓰는 건 애매한 게 아니라 정확한 것이다
        assertThat(system).contains("모르면 모른다고 써라");
    }

    @Test
    void 판정이_가격을_먼저_따지게_되어있다() {
        /*
         * ★ 순서가 중요하다.
         *
         * 처음에는 비중을 1순위로 뒀었다. 그랬더니 모든 판정이 "비중을 줄이세요" 로 끝났다.
         * 사용자가 알고 싶은 건 비중 조절이 아니라 "지금 가격에서 오르냐 내리냐" 였다.
         *
         * 비중을 뺀 게 아니라 순서를 내렸다. 가격이 먼저고 비중은 크기를 조절하는 근거다.
         */
        String system = builder.systemPrompt();

        assertThat(system).contains("1순위는 가격이다");
        assertThat(system).contains("비중은 마지막이다");
        assertThat(system).contains("비중만으로 판정을 뒤집지 마라");
        // 근거가 모자라면 방향을 지어내지 말고 보류해야 한다
        assertThat(system).contains("UNCLEAR");
    }

    @Test
    void 평단가까지_갈_수_있는지를_따지게_되어있다() {
        // 손실 중인 종목에서 제일 먼저 궁금한 건 "여기서 본전까지 가냐" 다.
        // 필요한 상승폭만 적으면 부족하고, 그 가격이 말이 되는 값인지까지 따져야 한다.
        String system = builder.systemPrompt();

        assertThat(system).contains("평단가까지 갈 수 있나");
        assertThat(system).contains("평단가가 이 회사가 받아본 적 없는 배수면");
        assertThat(system).contains("현재가·적정가·평단가 셋을 모두 숫자로 넣어라");
    }

    @Test
    void 밸류에이션에서_적정가를_내놓게_되어있다() {
        /*
         * 숫자를 나열만 하고 "그래서 비싼가 싼가" 를 안 쓰면 읽을 이유가 없는 섹션이 된다.
         * 업종 평균이 아니라 그 회사의 과거 배수 범위를 쓰게 한 건, 회사마다 받는 배수가 달라서다.
         */
        String system = builder.systemPrompt();

        assertThat(system).contains("여기서 적정가를 반드시 계산해라");
        assertThat(system).contains("그 회사의 과거 범위");
        assertThat(system).contains("범위로 적어라");
        // 적자 기업은 PER 이 없으므로 대안을 지시해야 한다
        assertThat(system).contains("적자라 PER 을 못 쓰면");
    }

    @Test
    void 레버리지_상품은_적정_비중으로_답하게_되어있다() {
        // 3배 상품은 감쇠 때문에 적정가 계산 자체가 성립하지 않는다.
        // 그래도 "모르겠다" 로 끝내면 안 되므로 답을 바꿔서 내놓게 한다.
        String system = builder.systemPrompt();

        assertThat(system).contains("레버리지 상품은 비중 자체가 위험이다");
        assertThat(system).contains("적정가 대신 **적정 비중**을 계산해라");
    }

    /**
     * 안 가진 종목은 현재가만 넣는다. 이 분석은 공개 화면에 나가므로
     * 평단가·수량·손익·비중 줄이 하나라도 섞이면 안 된다.
     */
    @Test
    void 안_가진_종목은_보유현황_없이_현재가만() {
        CompanyAnalysisFacts base = soxl();
        CompanyAnalysisFacts facts = new CompanyAnalysisFacts(
                "MSFT", "마이크로소프트", "Microsoft Corporation", "US", "NASDAQ", "USD", "STOCK", null, null,
                false, null, new BigDecimal("518.0"), null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, List.of(), null, base.asOf());

        String prompt = builder.userPrompt(facts);

        assertThat(prompt).contains("보유하지 않은 종목이다");
        assertThat(prompt).contains("현재가: ");
        assertThat(prompt).doesNotContain("내 평단가", "보유 수량", "평가손익", "전체 자산", "보유처");
        assertThat(prompt).doesNotContain("POSITION_REVIEW 에서는 [보유현황] 의 숫자를 직접 인용해라");
    }

    @Test
    void 안_가진_종목_규칙은_지시문에_있다() {
        assertThat(builder.systemPrompt()).contains("## 보유하지 않은 종목").contains("applicable: false");
    }

    /**
     * 토큰 절감: 앱이 이미 가진 자료(뉴스 제목·기관 보유·과거 배수)는 프롬프트에 넣고 검색 목록에서 뺀다.
     * 예전에는 [과거 배수] 를 넣고도 "과거 PER 범위를 검색하라" 고 시켰다
     */
    @Test
    void 이미_넣은_자료는_검색하지_말라고_한다() {
        CompanyAnalysisFacts base = soxl();
        com.mystock.portfolio.external.filing.CompanyFinancials.Period fy =
                new com.mystock.portfolio.external.filing.CompanyFinancials.Period("FY2025 (2025-12-31 결산)",
                        new BigDecimal("1000"), new BigDecimal("200"), new BigDecimal("100"), null, null, null, null, null,
                        null, null, null, new BigDecimal("10"), new BigDecimal("50"), java.time.LocalDate.of(2025, 12, 31));
        var fin = com.mystock.portfolio.external.filing.CompanyFinancials.of("DEMO", "SEC EDGAR", "USD", "연결",
                List.of(fy), null, null, new BigDecimal("200"), "보통주", List.of())
                .withHistory(List.of(com.mystock.portfolio.external.filing.CompanyFinancials.Valuation.of(
                        fy, java.time.LocalDate.of(2025, 12, 31), new BigDecimal("180"))));
        CompanyAnalysisFacts facts = new CompanyAnalysisFacts(
                "DEMO", "데모", "Demo Corp", "US", "NASDAQ", "USD", "STOCK", null, null,
                false, null, new BigDecimal("200"), null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, List.of(), fin, base.asOf());
        ResearchHints hints = new ResearchHints(
                List.of("데모, 신제품 공개 (Mon, 05 Oct 2026)"),
                List.of("따라가는 큰 기관 10곳 중 3곳이 들고 있다", "피델리티 (FMR): 비중 1.20%, 앞 분기 대비 늘림 +5.0% (2026-06-30 분기말)"));

        String prompt = builder.userPrompt(facts, hints);

        assertThat(prompt).contains("[최근 뉴스 헤드라인]").contains("데모, 신제품 공개");
        assertThat(prompt).contains("[큰 기관의 보유").contains("피델리티 (FMR): 비중 1.20%");
        String guide = prompt.substring(prompt.indexOf("[검색 가이드]"), prompt.indexOf("[보유현황]"));
        assertThat(guide).contains("재무제표·과거 PER 범위·최근 뉴스·기관 동향 는 아래 자료에 있으니 검색하지 마라");
        assertThat(guide).doesNotContain("과거 PER·PBR 범위").doesNotContain(", 최근 뉴스");
        // 요청은 맨 끝
        assertThat(prompt.indexOf("[요청]")).isGreaterThan(prompt.indexOf("[큰 기관의 보유"));
    }

    @Test
    void 자료가_없으면_예전처럼_검색_목록에_남는다() {
        CompanyAnalysisFacts base = soxl();
        com.mystock.portfolio.external.filing.CompanyFinancials.Period fy =
                new com.mystock.portfolio.external.filing.CompanyFinancials.Period("FY2025", new BigDecimal("1"),
                        null, null, null, null, null, null, null, null, null, null, null, null, null);
        var fin = com.mystock.portfolio.external.filing.CompanyFinancials.of("DEMO", "SEC EDGAR", "USD", "연결",
                List.of(fy), null, null, null, "보통주", List.of());
        CompanyAnalysisFacts facts = new CompanyAnalysisFacts(
                "DEMO", "데모", null, "US", "NASDAQ", "USD", "STOCK", null, null,
                false, null, new BigDecimal("200"), null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, List.of(), fin, base.asOf());

        String prompt = builder.userPrompt(facts);

        assertThat(prompt).contains("과거 PER·PBR 범위").contains(", 최근 뉴스").doesNotContain("[최근 뉴스 헤드라인]");
    }

    // ── 테스트용 사실 만들기 ──────────────────────────────

    /** SOXL 한 종목에 몰린 계좌 (미국 ETF, 3배 레버리지, 비중 81%). 수량·금액은 데모 값 */
    private CompanyAnalysisFacts soxl() {
        return new CompanyAnalysisFacts(
                "SOXL", "SOXL", "Direxion Daily Semiconductor Bull 3X Shares",
                "US", "AMEX", "USD", "ETF",
                new BigDecimal("3.0"), new BigDecimal("170800060"),
                true, new BigDecimal("100"), new BigDecimal("122.25"), new BigDecimal("151.8182"),
                new BigDecimal("-19.47"),
                new BigDecimal("16381500"), new BigDecimal("20343639"),
                new BigDecimal("-3962139"), new BigDecimal("-19.47"),
                new BigDecimal("81.47"), new BigDecimal("20107402"),
                "나무증권 + 토스증권",
                new BigDecimal("160.41"), new BigDecimal("10.10"), new BigDecimal("-47.91"),
                60, new BigDecimal("15.02"), new BigDecimal("9.88"),
                List.of("이 한 종목이 전체 자산의 81.47% 를 차지한다. 단일 종목 집중 위험이 크다.",
                        "3배 레버리지 상품이다."),
                null,
                LocalDateTime.of(2026, 9, 14, 11, 0));
    }

    /** 국내 종목 (현대차) */
    private CompanyAnalysisFacts hyundai() {
        return new CompanyAnalysisFacts(
                "005380", "현대차", "HYUNDAI MOTOR", "KR", "KOSPI", "KRW", "STOCK",
                null, new BigDecimal("204757766"),
                true, new BigDecimal("5"), new BigDecimal("382000"), new BigDecimal("400000"),
                new BigDecimal("-4.50"),
                new BigDecimal("1910000"), new BigDecimal("2000000"),
                new BigDecimal("-90000"), new BigDecimal("-4.50"),
                new BigDecimal("9.50"), new BigDecimal("20107402"),
                "나무증권",
                new BigDecimal("62.30"), new BigDecimal("3.92"), new BigDecimal("2.1"),
                60, new BigDecimal("410000"), new BigDecimal("360000"),
                List.of(),
                null,
                LocalDateTime.of(2026, 9, 14, 11, 0));
    }

    /** 평단가를 못 구한 경우 */
    private CompanyAnalysisFacts withoutAveragePrice() {
        CompanyAnalysisFacts base = hyundai();
        return new CompanyAnalysisFacts(
                base.symbol(), base.name(), base.englishName(), base.marketCountry(), base.market(),
                base.currency(), base.securityType(), base.leverageFactor(), base.sharesOutstanding(),
                true, base.quantity(), base.lastPrice(), null, null,
                base.marketValueKrw(), base.purchaseKrw(), base.profitLossKrw(), base.profitRatePercent(),
                base.weightPercent(), base.totalValueKrw(), base.brokers(),
                base.annualizedVolatilityPercent(), base.dailyVolatilityPercent(), base.periodReturnPercent(),
                base.dataPoints(), base.periodHigh(), base.periodLow(), base.riskFlags(), base.financials(), base.asOf());
    }

    /** 시세 통계를 못 구한 경우 */
    private CompanyAnalysisFacts withoutMarketStats() {
        CompanyAnalysisFacts base = hyundai();
        return new CompanyAnalysisFacts(
                base.symbol(), base.name(), base.englishName(), base.marketCountry(), base.market(),
                base.currency(), base.securityType(), base.leverageFactor(), base.sharesOutstanding(),
                true, base.quantity(), base.lastPrice(), base.averagePurchasePrice(), base.priceGapPercent(),
                base.marketValueKrw(), base.purchaseKrw(), base.profitLossKrw(), base.profitRatePercent(),
                base.weightPercent(), base.totalValueKrw(), base.brokers(),
                null, null, null, null, null, null, base.riskFlags(), base.financials(), base.asOf());
    }
}
