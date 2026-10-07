package com.mystock.portfolio.brokerage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mystock.portfolio.external.namuh.NamuhApiProperties;
import com.mystock.portfolio.external.namuh.NamuhHoldingsService;
import com.mystock.portfolio.external.namuh.dto.NamuhGbBalanceResponse;
import com.mystock.portfolio.external.namuh.dto.NamuhKrBalanceResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 나무 잔고 응답에서 대사용 합계를 꺼내는지. 응답 모양은 나무 문서의 필드 이름 그대로, 금액은 데모 값.
 * 수량 0 인 줄은 표에서 빠지므로 앱 합계에서도 빠지고, 그 줄에 금액이 있으면 대사가 차이를 잡는다
 */
class NamuhStatementTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void 국내와_해외_합계를_각각_돌려주고_걸러진_줄은_앱_합계에서_빠진다() throws Exception {
        NamuhKrBalanceResponse kr = JSON.readValue("""
                {"rsp_cd":"00000","Output_0":{"tot_eal_amt":15000},
                 "Output_1":[
                   {"iem_cd":"005930","iem_nm":"데모전자","itg_bnc_qty":10,"now_pr":1000,"phs_pr":900,"eal_amt":10000,"eal_pls_amt":1000},
                   {"iem_cd":"000660","iem_nm":"매도한종목","itg_bnc_qty":0,"now_pr":5000,"eal_amt":5000,"eal_pls_amt":0}
                 ]}""", NamuhKrBalanceResponse.class);
        NamuhGbBalanceResponse gb = JSON.readValue("""
                {"rsp_cd":"00000","Output_0":{"eal_amt_sum":30000},
                 "Output_1":[{"iem_cd":"SOXL","iem_nm":"데모ETF","cns_bse_bnc_qty":2,"fc_sec_end_pr":10,
                              "krw_eal_amt":30000,"krw_abk_amt1":28000,"cur_cd":"USD"}]}""", NamuhGbBalanceResponse.class);

        NamuhHoldingsService holdings = mock(NamuhHoldingsService.class);
        when(holdings.domesticBalance(any())).thenReturn(kr);
        when(holdings.overseasBalance(any())).thenReturn(gb);

        BrokerageStatement s = new NamuhBrokerageClient(holdings, mock(NamuhApiProperties.class)).statement(null);

        assertThat(s.holdings()).extracting(BrokerageHolding::symbol).containsExactly("005930", "SOXL");
        assertThat(s.totals()).hasSize(2);
        BrokerageStatement.ReportedTotal domestic = s.totals().get(0);
        assertThat(domestic.scope()).isEqualTo("국내");
        assertThat(domestic.brokerTotal()).isEqualByComparingTo("15000");
        assertThat(domestic.appSum()).isEqualByComparingTo("10000");   // 수량 0 인 줄(5,000)이 빠짐 → 대사가 잡을 차이
        assertThat(s.totals().get(1).appSum()).isEqualByComparingTo("30000");
    }

    @Test
    void 해외_조회가_실패해도_국내는_남고_해외_합계_줄은_없다() throws Exception {
        NamuhKrBalanceResponse kr = JSON.readValue("""
                {"rsp_cd":"00000","Output_0":{"tot_eal_amt":10000},
                 "Output_1":[{"iem_cd":"005930","iem_nm":"데모전자","itg_bnc_qty":10,"now_pr":1000,"eal_amt":10000,"eal_pls_amt":0}]}""",
                NamuhKrBalanceResponse.class);
        NamuhHoldingsService holdings = mock(NamuhHoldingsService.class);
        when(holdings.domesticBalance(any())).thenReturn(kr);
        when(holdings.overseasBalance(any())).thenThrow(new IllegalStateException("해외 계좌 없음"));

        BrokerageStatement s = new NamuhBrokerageClient(holdings, mock(NamuhApiProperties.class)).statement(null);

        assertThat(s.holdings()).hasSize(1);
        assertThat(s.totals()).extracting(BrokerageStatement.ReportedTotal::scope).containsExactly("국내");
    }
}
