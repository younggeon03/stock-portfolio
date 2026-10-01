package com.mystock.portfolio.external.namuh.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * POST /krstock/inquiry/v1/balance (국내 주식잔고조회) 응답.
 *
 * ★ 필드 이름이 전부 축약어라 읽기가 어렵다. 뜻을 주석으로 달아둔다.
 * 나무증권 API 는 오래된 원장 시스템 필드명을 그대로 노출하는 편이다.
 *
 * Output_0 = 계좌 전체 요약
 * Output_1 = 종목별 목록
 */
public record NamuhKrBalanceResponse(

        @JsonProperty("Output_0") Summary summary,

        @JsonProperty("Output_1") List<Item> items,

        @JsonProperty("rsp_cd") String rspCd,

        @JsonProperty("rsp_msg") String rspMsg

) implements NamuhResponse {

    /**
     * 요약(Output_0)이 왔으면 정상 조회로 본다.
     * 보유 종목이 하나도 없어도 요약은 내려오므로, items 가 비어 있는 것은 오류가 아니다.
     */
    @Override
    public boolean hasPayload() {
        return summary != null || (items != null && !items.isEmpty());
    }

    /** 계좌 요약 */
    public record Summary(

            /** 예수금 (deposit cash amount) */
            @JsonProperty("dca") BigDecimal deposit,

            /** 총 자산금액 */
            @JsonProperty("tot_aet_amt") BigDecimal totalAssetAmount,

            /** 총 매입금액 */
            @JsonProperty("tot_byn_amt") BigDecimal totalPurchaseAmount,

            /** 총 평가금액 */
            @JsonProperty("tot_eal_amt") BigDecimal totalEvaluationAmount,

            /** 총 평가손익 */
            @JsonProperty("tot_eal_pls") BigDecimal totalProfitLoss,

            /** 수익률 (%). 이미 퍼센트 단위다. 토스처럼 소수가 아니다 */
            @JsonProperty("pft_rt") BigDecimal profitRatePercent
    ) {
    }

    /** 보유 종목 한 건 */
    public record Item(

            /** 종목명 (iem = item) */
            @JsonProperty("iem_nm") String name,

            /** 종목코드. 6자리 */
            @JsonProperty("iem_cd") String symbol,

            /** 구분명 ("현금" 등) */
            @JsonProperty("tp_cd_nm") String typeName,

            /** 통합 잔고 수량 */
            @JsonProperty("itg_bnc_qty") BigDecimal quantity,

            /** 잔여 수량 (미결제 포함) */
            @JsonProperty("rsdl_qty") BigDecimal remainingQuantity,

            /** 매입 단가 (purchase price) */
            @JsonProperty("phs_pr") BigDecimal averagePurchasePrice,

            /** 현재가 */
            @JsonProperty("now_pr") BigDecimal lastPrice,

            /** 평가금액 */
            @JsonProperty("eal_amt") BigDecimal evaluationAmount,

            /** 평가손익 금액 */
            @JsonProperty("eal_pls_amt") BigDecimal profitLossAmount,

            /** 수익률 (%) */
            @JsonProperty("pft_rt") BigDecimal profitRatePercent
    ) {
    }

    /**
     * 국내 주식잔고 조회 요청 본문.
     *
     * 코드값의 의미는 문서 예시를 그대로 따랐다.
     * 잘못 넣으면 빈 결과가 오거나 rsp_cd 오류가 난다.
     */
    public record Request(@JsonProperty("Input_0") Input input) {

        public record Input(

                /** 계좌번호 */
                @JsonProperty("act_no") String accountNo,

                /** 잔고 기준 코드 */
                @JsonProperty("bnc_bse_cd") String balanceBaseCode,

                /** 대출/기타 구분 코드 */
                @JsonProperty("ltg_aot_dit_cd") String loanDivisionCode,

                /** 자산 기준 */
                @JsonProperty("aet_bse") String assetBase,

                /** 수량 구분 코드 */
                @JsonProperty("qut_dit_cd") String quantityDivisionCode,

                /** 조회 수량 코드 */
                @JsonProperty("aly_qut_cd") String inquiryQuantityCode
        ) {
        }

        /** 문서 예시와 동일한 기본값으로 요청을 만든다 */
        public static Request of(String accountNo) {
            return new Request(new Input(accountNo, "1", "9", "2", "UNT", "2"));
        }
    }
}
