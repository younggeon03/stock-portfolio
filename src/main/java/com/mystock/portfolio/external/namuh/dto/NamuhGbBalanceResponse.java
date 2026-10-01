package com.mystock.portfolio.external.namuh.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * POST /gbstock/inquiry/v1/balance (해외 주식잔고조회) 응답.
 *
 * gb = global(해외), fc = foreign currency(외화), krw = 원화 환산.
 *
 * ★ 토스보다 편한 점
 * 해외 종목인데도 원화 환산 금액(krw_eal_amt)을 같이 준다.
 * 그래서 환율을 따로 구해서 곱할 필요가 없다.
 */
public record NamuhGbBalanceResponse(

        @JsonProperty("Output_0") Summary summary,

        @JsonProperty("Output_1") List<Item> items,

        @JsonProperty("rsp_cd") String rspCd,

        @JsonProperty("rsp_msg") String rspMsg

) implements NamuhResponse {

    /** 요약(Output_0)이 왔으면 정상 조회로 본다 */
    @Override
    public boolean hasPayload() {
        return summary != null || (items != null && !items.isEmpty());
    }

    /** 계좌 요약 */
    public record Summary(

            /** 원화 환산 평가금액 합계 */
            @JsonProperty("eal_amt_sum") BigDecimal evaluationAmountSumKrw,

            /** 원화 환산 평가손익 합계 */
            @JsonProperty("eal_pls_sum_amt") BigDecimal profitLossSumKrw,

            /** 원화 기준 수익률 (%) */
            @JsonProperty("krw_pft_rt") BigDecimal profitRatePercentKrw,

            /** 외화 평가금액 */
            @JsonProperty("fc_eal_amt") BigDecimal evaluationAmountForeign,

            /** 외화 기준 수익률 (%) */
            @JsonProperty("pft_rt") BigDecimal profitRatePercentForeign
    ) {
    }

    /** 보유 종목 한 건 */
    public record Item(

            /** 거래 국가 코드. 200 = 미국 */
            @JsonProperty("fc_sec_trd_nat_cd") String countryCode,

            /** 거래 국가명 */
            @JsonProperty("fc_sec_trd_nat_nm") String countryName,

            /** 종목코드(티커) */
            @JsonProperty("iem_cd") String symbol,

            /** 종목명 */
            @JsonProperty("iem_nm") String name,

            /** 결제 기준 잔고 수량 */
            @JsonProperty("cns_bse_bnc_qty") BigDecimal quantity,

            /** 외화 매입 단가 */
            @JsonProperty("fc_phs_uit_pr") BigDecimal averagePurchasePriceForeign,

            /** 외화 현재가(종가) */
            @JsonProperty("fc_sec_end_pr") BigDecimal lastPriceForeign,

            /** 외화 매입금액 */
            @JsonProperty("fc_abk_amt") BigDecimal purchaseAmountForeign,

            /** 외화 평가금액 */
            @JsonProperty("fc_eal_amt") BigDecimal evaluationAmountForeign,

            /** 원화 환산 매입금액 */
            @JsonProperty("krw_abk_amt1") BigDecimal purchaseAmountKrw,

            /** 원화 환산 평가금액 */
            @JsonProperty("krw_eal_amt") BigDecimal evaluationAmountKrw,

            /** 원화 환산 평가손익 */
            @JsonProperty("krw_eal_pls_amt") BigDecimal profitLossAmountKrw,

            /** 평가 수익률 (%). 외화 기준 */
            @JsonProperty("eal_pft_rt") BigDecimal profitRatePercent,

            /** 통화 코드 (USD 등) */
            @JsonProperty("cur_cd") String currency,

            /** 매입 환율 */
            @JsonProperty("phs_xcg_rt") BigDecimal purchaseExchangeRate,

            /** 전일 기준 환율 */
            @JsonProperty("tdt_sby_bse_xcg_rt") BigDecimal baseExchangeRate
    ) {
    }

    /** 해외 주식잔고 조회 요청 본문 */
    public record Request(@JsonProperty("Input_0") Input input) {

        public record Input(

                /** 계좌번호 */
                @JsonProperty("act_no") String accountNo,

                /** 조회 구분 코드 */
                @JsonProperty("qut_iqr_dit_cd") String inquiryDivisionCode,

                /** 거래 국가 코드. 200 = 미국 */
                @JsonProperty("fc_sec_trd_nat_cd") String countryCode,

                /** 표시 통화. KRW 로 주면 원화 환산 값을 채워준다 */
                @JsonProperty("cur_cd") String currency,

                /** 환산 구분 코드 */
                @JsonProperty("xns_dit_cd") String conversionDivisionCode
        ) {
        }

        /** 문서 예시와 동일한 기본값 (미국 주식, 원화 표시) */
        public static Request of(String accountNo) {
            return new Request(new Input(accountNo, "9", "200", "KRW", "0"));
        }
    }
}
