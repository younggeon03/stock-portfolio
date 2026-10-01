package com.mystock.portfolio.external.dart;

import com.mystock.portfolio.domain.DartCorpCode;
import com.mystock.portfolio.external.filing.CompanyFinancials;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DART 응답을 숫자로 바꾸는 부분.
 *
 * 숫자는 2026-09-23 에 실제로 받은 삼성전자 연결재무제표(2025 사업보고서, 2026 반기보고서)다.
 * 주식수와 현재가만 계산을 확인하려고 정한 값이다.
 */
class DartFinancialServiceTest {

    private static final BigDecimal SHARES = new BigDecimal("5919637922");
    private static final BigDecimal PRICE = new BigDecimal("100000");

    @Test
    void 사업보고서_한_건에서_세_해를_오래된_순서로_꺼낸다() {
        CompanyFinancials d = DartFinancialService.build("삼성전자", 2025, annualRows(),
                null, null, List.of(), SHARES, PRICE, null);

        assertThat(d.statementKind()).isEqualTo("연결");
        assertThat(d.annual()).extracting(CompanyFinancials.Period::label)
                .containsExactly("2023년", "2024년", "2025년");

        CompanyFinancials.Period y2025 = d.annual().get(2);
        assertThat(y2025.revenue()).isEqualByComparingTo("333605938000000");
        assertThat(y2025.netIncome()).isEqualByComparingTo("45206805000000");
        assertThat(y2025.debtRatio()).isEqualByComparingTo("29.94");
        assertThat(y2025.roe()).isEqualByComparingTo("10.36");
    }

    @Test
    void 반기보고서가_있으면_최근_4분기로_PER_을_잰다() {
        CompanyFinancials d = DartFinancialService.build("삼성전자", 2025, annualRows(),
                "상반기", "반기보고서", interimRows(), SHARES, PRICE, null);

        // 누적은 3개월치(thstrm_amount)가 아니라 연초부터 누적(thstrm_add_amount)이어야 한다
        assertThat(d.interim().revenue()).isEqualByComparingTo("305372914000000");
        assertThat(d.interim().roe()).as("반기 이익으로 ROE 를 재면 반토막이 난다").isNull();

        // 2025 연간 + 2026 상반기 누적 - 2025 상반기 누적
        assertThat(d.ttm().revenue()).isEqualByComparingTo("485272032000000");
        assertThat(d.ttm().netIncome()).isEqualByComparingTo("150717225000000");
        assertThat(d.ttm().eps()).isEqualByComparingTo("25461");

        assertThat(d.per()).isEqualByComparingTo("3.93");
        // PBR 은 연말이 아니라 가장 최근(반기말) 자본으로
        assertThat(d.pbr()).isEqualByComparingTo("1.02");

        assertThat(d.sources()).extracting(CompanyFinancials.Source::title)
                .containsExactly("DART 2025년 사업보고서", "DART 2026년 반기보고서");
        assertThat(d.sources().get(0).url()).endsWith("rcpNo=20260310000001");
    }

    @Test
    void 연결이_없으면_별도를_쓴다() {
        List<DartAccountRow> separateOnly = annualRows().stream()
                .map(r -> new DartAccountRow(r.receiptNo(), "OFS", r.sjDiv(), r.accountName(),
                        r.thisTermName(), r.thisTermDate(), r.thisTermAmount(), r.thisTermCumulative(),
                        r.priorTermAmount(), r.priorTermCumulative(), r.twoTermsAgoAmount()))
                .toList();

        CompanyFinancials d = DartFinancialService.build("삼성전자", 2025, separateOnly,
                null, null, List.of(), SHARES, PRICE, null);

        assertThat(d.statementKind()).isEqualTo("별도");
        assertThat(d.annual()).hasSize(3);
    }

    @Test
    void 적자면_PER_을_비운다() {
        List<DartAccountRow> loss = List.of(
                annual("IS", "당기순이익(손실)", "-1,000", "500", "500"),
                annual("BS", "자본총계", "10,000", "10,000", "10,000"));

        CompanyFinancials d = DartFinancialService.build("적자회사", 2025, loss,
                null, null, List.of(), new BigDecimal("10"), PRICE, null);

        assertThat(d.per()).isNull();
        assertThat(d.pbr()).isNotNull();
    }

    @Test
    void 주식수는_보통주와_우선주를_더하고_자기주식을_뺀다() {
        // 2026-09-27 에 받은 현대차 2026 반기보고서의 주식 총수 현황
        List<DartShareRow> hyundai = List.of(
                share("보통주", "204,757,766", "2,341,700", "202,416,066"),
                share("우선주", "60,632,342", "1,214,332", "59,418,010"),
                share("합계", "265,390,108", "3,556,032", "261,834,076"),
                share("비고", "-", "-", "-"));

        assertThat(DartFinancialService.outstandingShares(hyundai)).isEqualByComparingTo("261834076");
    }

    @Test
    void 합계_줄이_없으면_보통주와_우선주를_직접_더한다() {
        // 삼성전자처럼 우선주에 자기주식이 없으면 "-" 로 온다
        List<DartShareRow> noTotal = List.of(
                share("보통주", "5,846,278,608", "82,086,705", ""),
                share("우선주", "802,371,203", "-", ""));

        // 유통주식수 칸이 비었으니 발행 − 자기주식으로 계산해 더한다
        assertThat(DartFinancialService.outstandingShares(noTotal))
                .isEqualByComparingTo(new BigDecimal("5764191903").add(new BigDecimal("802371203")));
    }

    @Test
    void 주식수를_못_구하면_null() {
        assertThat(DartFinancialService.outstandingShares(List.of())).isNull();
        assertThat(DartFinancialService.outstandingShares(List.of(share("비고", "-", "-", "-")))).isNull();
    }

    @Test
    void 비지배지분이_있으면_주당_지표를_지배주주_몫으로_잰다() {
        // 2026-09-27 에 받은 현대차 2025 사업보고서·2026 반기보고서 (백만원 단위를 원으로)
        List<DartAccountRow> annual = List.of(
                annual("IS", "당기순이익(손실)", "10,364,775,000,000", "13,229,908,000,000", "12,272,301,000,000"),
                annual("BS", "부채총계", "200,000,000,000,000", "200,000,000,000,000", "200,000,000,000,000"),
                annual("BS", "자본총계", "127,648,237,000,000", "120,275,933,000,000", "101,809,440,000,000"));
        List<DartAccountRow> interim = List.of(
                balance("자본총계", "135,416,445,000,000"),
                income("당기순이익(손실)", "2,887,962,000,000", "5,472,888,000,000", "6,632,564,000,000"));
        List<DartOwnerRow> annualOwners = List.of(
                owner("IS", DartOwnerRow.OWNERS_PROFIT, "9,445,987,000,000", null, "12,526,691,000,000", null,
                        "11,961,717,000,000"),
                owner("BS", DartOwnerRow.OWNERS_EQUITY, "115,446,507,000,000", null, "109,103,398,000,000", null,
                        "92,497,311,000,000"),
                // 자본변동표에도 같은 ID 가 칸마다 다른 뜻으로 나온다. 이걸 집으면 안 된다
                owner("SCE", DartOwnerRow.OWNERS_PROFIT, "0", null, "0", null, "0"));
        List<DartOwnerRow> interimOwners = List.of(
                owner("IS", DartOwnerRow.OWNERS_PROFIT, "2,520,851,000,000", "4,856,197,000,000", null,
                        "6,155,597,000,000", null),
                owner("BS", DartOwnerRow.OWNERS_EQUITY, "122,327,997,000,000", null, "115,446,507,000,000", null, null));

        CompanyFinancials d = DartFinancialService.build("현대자동차", 2025, annual, "상반기", "반기보고서", interim,
                new BigDecimal("261834076"), new BigDecimal("357000"), null, annualOwners, interimOwners);

        // 순이익 열은 연결 그대로 둔다
        assertThat(d.annual().get(2).netIncome()).isEqualByComparingTo("10364775000000");
        assertThat(d.annual().get(2).ownersNetIncome()).isEqualByComparingTo("9445987000000");
        assertThat(d.annual().get(2).roe()).isEqualByComparingTo("8.18");
        // 부채비율은 관례대로 자본총계로 나눈다
        assertThat(d.annual().get(2).debtRatio()).isEqualByComparingTo("156.68");

        // 2025 지배 + 2026 상반기 지배 누적 − 2025 상반기 지배 누적
        assertThat(d.ttm().ownersNetIncome()).isEqualByComparingTo("8146587000000");
        assertThat(d.ttm().eps()).as("연결 순이익으로 재면 35,156").isEqualByComparingTo("31114");
        assertThat(d.ttm().roe()).isEqualByComparingTo("6.66");
        assertThat(d.interim().bps()).isEqualByComparingTo("467197");

        assertThat(d.per()).isEqualByComparingTo("11.47");
        assertThat(d.pbr()).isEqualByComparingTo("0.76");
    }

    @Test
    void 지배주주_몫을_못_받으면_연결_순이익으로_잰다() {
        // 전체 재무제표 호출이 실패해도 주당 지표가 비면 안 된다
        CompanyFinancials d = DartFinancialService.build("삼성전자", 2025, annualRows(),
                "상반기", "반기보고서", interimRows(), SHARES, PRICE, null, List.of(), List.of());

        assertThat(d.ttm().ownersNetIncome()).isNull();
        assertThat(d.ttm().eps()).isEqualByComparingTo("25461");
    }

    @Test
    void 결산일은_재무상태표_기준일에서_읽는다() {
        // 3월 결산 회사가 있어서 12월 31일로 가정하면 과거 PER 을 엉뚱한 날 주가로 잰다
        List<DartAccountRow> march = List.of(new DartAccountRow("r", "CFS", "BS", "자산총계", "제 30 기말",
                "2026.03.31 현재", "1", null, "1", null, "1"));
        assertThat(DartFinancialService.fiscalYearEnd(march, 2025)).isEqualTo(java.time.LocalDate.of(2026, 3, 31));

        // 기준일이 없으면 12월 31일
        assertThat(DartFinancialService.fiscalYearEnd(annualRows(), 2025))
                .isEqualTo(java.time.LocalDate.of(2025, 12, 31));

        // 연간 기간마다 결산일이 한 해씩 붙는다
        CompanyFinancials d = DartFinancialService.build("삼성전자", 2025, annualRows(),
                null, null, List.of(), SHARES, PRICE, null);
        assertThat(d.annual()).extracting(CompanyFinancials.Period::periodEnd).containsExactly(
                java.time.LocalDate.of(2023, 12, 31), java.time.LocalDate.of(2024, 12, 31),
                java.time.LocalDate.of(2025, 12, 31));
    }

    @Test
    void 금액_글자를_숫자로() {
        assertThat(DartFinancialService.amount("1,234,000")).isEqualByComparingTo("1234000");
        assertThat(DartFinancialService.amount("-11,526")).isEqualByComparingTo("-11526");
        assertThat(DartFinancialService.amount("-")).isNull();
        assertThat(DartFinancialService.amount("")).isNull();
        assertThat(DartFinancialService.amount(null)).isNull();
    }

    @Test
    void 고유번호_파일에서_상장사만_남긴다() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <result>
                    <list>
                        <corp_code>00126380</corp_code>
                        <corp_name>삼성전자</corp_name>
                        <stock_code>005930</stock_code>
                        <modify_date>20251201</modify_date>
                    </list>
                    <list>
                        <corp_code>00434003</corp_code>
                        <corp_name>비상장회사</corp_name>
                        <stock_code> </stock_code>
                        <modify_date>20170630</modify_date>
                    </list>
                    <list>
                        <corp_code>00111111</corp_code>
                        <corp_name>에이&amp;비</corp_name>
                        <stock_code>123456</stock_code>
                        <modify_date>20200101</modify_date>
                    </list>
                </result>
                """;

        List<DartCorpCode> listed = DartApiClient.parseListed(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        assertThat(listed).extracting(DartCorpCode::getStockCode).containsExactly("005930", "123456");
        assertThat(listed.get(0).getCorpCode()).isEqualTo("00126380");
        assertThat(listed.get(1).getCorpName()).isEqualTo("에이&비");
    }

    // ── 삼성전자 실제 응답에서 옮긴 줄 ──────────────────────

    private static List<DartAccountRow> annualRows() {
        return List.of(
                annual("BS", "자산총계", "566,942,110,000,000", "514,531,948,000,000", "455,905,980,000,000"),
                annual("BS", "부채총계", "130,621,773,000,000", "112,339,878,000,000", "92,228,115,000,000"),
                annual("BS", "자본총계", "436,320,337,000,000", "402,192,070,000,000", "363,677,865,000,000"),
                annual("IS", "매출액", "333,605,938,000,000", "300,870,903,000,000", "258,935,494,000,000"),
                annual("IS", "영업이익", "43,601,051,000,000", "32,725,961,000,000", "6,566,976,000,000"),
                annual("IS", "당기순이익(손실)", "45,206,805,000,000", "34,451,351,000,000", "15,487,100,000,000"),
                // 별도재무제표 줄이 섞여 와도 연결만 써야 한다
                new DartAccountRow("20260310000001", "OFS", "IS", "매출액", null, null,
                        "238,043,009,000,000", null, "209,052,241,000,000", null, "170,374,090,000,000"));
    }

    private static List<DartAccountRow> interimRows() {
        return List.of(
                balance("자산총계", "759,480,516,000,000"),
                balance("부채총계", "180,170,840,000,000"),
                balance("자본총계", "579,309,676,000,000"),
                income("매출액", "171,499,470,000,000", "305,372,914,000,000", "153,706,820,000,000"),
                income("영업이익", "89,492,412,000,000", "146,725,209,000,000", "11,361,329,000,000"),
                income("당기순이익(손실)", "71,624,461,000,000", "118,849,733,000,000", "13,339,313,000,000"));
    }

    private static DartAccountRow annual(String sj, String name, String thisYear, String lastYear, String twoYearsAgo) {
        return new DartAccountRow("20260310000001", "CFS", sj, name, null, null,
                thisYear, null, lastYear, null, twoYearsAgo);
    }

    private static DartOwnerRow owner(String sj, String accountId, String thisTerm, String thisCumulative,
                                      String prior, String priorCumulative, String twoAgo) {
        return new DartOwnerRow(sj, accountId, thisTerm, thisCumulative, prior, priorCumulative, twoAgo);
    }

    private static DartShareRow share(String kind, String issued, String treasury, String outstanding) {
        return new DartShareRow("20260814003521", kind, issued, treasury, outstanding, "2026-06-30");
    }

    private static DartAccountRow balance(String name, String amount) {
        return new DartAccountRow("20260814000002", "CFS", "BS", name, null, null,
                amount, null, null, null, null);
    }

    /** 반기 손익: 3개월치, 올해 누적, 작년 같은 기간 누적 */
    private static DartAccountRow income(String name, String quarter, String ytd, String priorYtd) {
        return new DartAccountRow("20260814000002", "CFS", "IS", name, null, null,
                quarter, ytd, null, priorYtd, null);
    }
}
