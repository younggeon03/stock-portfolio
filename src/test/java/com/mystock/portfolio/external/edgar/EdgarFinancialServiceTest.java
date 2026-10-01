package com.mystock.portfolio.external.edgar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mystock.portfolio.domain.SecCik;
import com.mystock.portfolio.external.filing.CompanyFinancials;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EDGAR 응답을 숫자로 바꾸는 부분.
 *
 * avgo-companyfacts.json 은 2026-09-23 에 실제로 받은 브로드컴 응답에서 쓰는 태그만 남긴 것이다.
 * 기댓값은 자바 코드가 아니라 원본 JSON 을 따로 계산해 구했다. 같은 로직으로 기댓값을 만들면 틀려도 통과한다.
 */
class EdgarFinancialServiceTest {

    private static final long CIK = 1730168L;
    /** 10-Q 표지의 발행주식수 */
    private static final BigDecimal SHARES = new BigDecimal("4773629865");
    private static final BigDecimal PRICE = new BigDecimal("363.5");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void 결산일이_11월인_회계연도를_세_해_꺼낸다() throws Exception {
        CompanyFinancials d = EdgarFinancialService.build(broadcom(), CIK, SHARES, PRICE);

        assertThat(d.corpName()).isEqualTo("Broadcom Inc.");
        assertThat(d.currency()).isEqualTo("USD");
        assertThat(d.annual()).extracting(CompanyFinancials.Period::label).containsExactly(
                "FY2023 (2023-10-29 결산)", "FY2024 (2024-11-03 결산)", "FY2025 (2025-11-02 결산)");

        CompanyFinancials.Period fy2025 = d.annual().get(2);
        assertThat(fy2025.revenue()).isEqualByComparingTo("63887000000");
        assertThat(fy2025.operatingIncome()).isEqualByComparingTo("25484000000");
        assertThat(fy2025.totalLiabilities()).isEqualByComparingTo("89800000000");
        assertThat(fy2025.totalEquity()).isEqualByComparingTo("81292000000");
        assertThat(fy2025.debtRatio()).isEqualByComparingTo("110.47");
        // 과거 PER 을 잴 종가 날짜. 달력 연말이 아니라 실제 결산일이어야 한다
        assertThat(fy2025.periodEnd()).isEqualTo(java.time.LocalDate.of(2025, 11, 2));
    }

    @Test
    void 태그가_바뀌어도_최근_기간까지_있는_태그를_쓴다() throws Exception {
        // 브로드컴 순이익은 NetIncomeLoss 가 FY2024 에서 끊기고 ProfitLoss 로 이어진다.
        // 우선순위만 보고 NetIncomeLoss 를 쓰면 FY2025 순이익이 비어 버린다
        CompanyFinancials d = EdgarFinancialService.build(broadcom(), CIK, SHARES, PRICE);

        assertThat(d.annual().get(2).netIncome()).isEqualByComparingTo("23126000000");
        assertThat(d.annual().get(2).roe()).isEqualByComparingTo("28.45");
    }

    @Test
    void 최근_10Q_누적으로_최근_4분기와_PER_을_잰다() throws Exception {
        CompanyFinancials d = EdgarFinancialService.build(broadcom(), CIK, SHARES, PRICE);

        // 3개월치(29,591M)가 아니라 회계연도 시작부터 누적이어야 한다
        assertThat(d.interim().label()).isEqualTo("FY2026 Q3 누적 (~2026-08-02)");
        assertThat(d.interim().revenue()).isEqualByComparingTo("71089000000");
        assertThat(d.interim().roe()).isNull();

        // FY2025 + FY2026 Q3 누적 − FY2025 Q3 누적(~2025-08-03)
        assertThat(d.ttm().revenue()).isEqualByComparingTo("89104000000");
        assertThat(d.ttm().netIncome()).isEqualByComparingTo("38265000000");
        assertThat(d.ttm().eps()).isEqualByComparingTo("8.02");

        assertThat(d.per()).isEqualByComparingTo("45.32");
        assertThat(d.pbr()).isEqualByComparingTo("17.41");
    }

    @Test
    void 공시_원문_주소를_만든다() throws Exception {
        CompanyFinancials d = EdgarFinancialService.build(broadcom(), CIK, SHARES, PRICE);

        assertThat(d.sources()).extracting(CompanyFinancials.Source::title)
                .containsExactly("SEC 10-K FY2025", "SEC 10-Q FY2026 Q3");
        assertThat(d.sources().get(1).url())
                .isEqualTo("https://www.sec.gov/Archives/edgar/data/1730168/000173016826000080/");
    }

    @Test
    void 토스가_막혀_주식수가_없으면_SEC_표지_주식수를_쓴다() throws Exception {
        CompanyFinancials d = EdgarFinancialService.build(broadcom(), CIK, null, PRICE);

        assertThat(d.ttm().eps()).isEqualByComparingTo("8.02");
    }

    @Test
    void 티커_매핑에서_중복은_앞의_것을_남긴다() throws Exception {
        JsonNode root = objectMapper.readTree("""
                {"0":{"cik_str":1730168,"ticker":"AVGO","title":"Broadcom Inc."},
                 "1":{"cik_str":1067983,"ticker":"brk-b","title":"BERKSHIRE HATHAWAY INC"},
                 "2":{"cik_str":9999999,"ticker":"AVGO","title":"중복"}}
                """);

        List<SecCik> ciks = EdgarApiClient.parseTickers(root);

        assertThat(ciks).extracting(SecCik::getTicker).containsExactly("AVGO", "BRK-B");
        assertThat(ciks.get(0).getCik()).isEqualTo(1730168L);
    }

    private JsonNode broadcom() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/edgar/avgo-companyfacts.json")) {
            return objectMapper.readTree(in);
        }
    }
}
