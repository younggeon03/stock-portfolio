package com.mystock.portfolio.external.namuh.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * POST /gbstock/inquiry/v1/periodPnl (해외주식 기간손익) 응답.
 *
 * ★ 나무는 실현손익을 계산해서 준다
 * 토스는 체결 내역만 줘서 앱이 선입선출로 계산하지만, 나무는 이 API 가 기간 합계와 날짜별 손익을 준다.
 * 조회구분을 2(원화기준)로 주면 이름은 fc_(외화) 로 시작해도 값은 원화다.
 * 취득가 계산 방식(선입선출·이동평균)은 고객이 나무에서 고른 방식을 따른다.
 *
 * 문서: 응답 블록은 데이터가 있을 때만 내려온다. 그 해에 판 게 없으면 Output_0 이 없다.
 */
public record NamuhPeriodPnlResponse(

        @JsonProperty("Output_0") Summary summary,

        @JsonProperty("Output_1") List<Daily> daily,

        @JsonProperty("rsp_cd") String rspCd,

        @JsonProperty("rsp_msg") String rspMsg

) implements NamuhResponse {

    /**
     * 판 게 없는 해에는 데이터 블록 없이 안내 문구만 온다. 그것도 정상 조회다.
     * 진짜 오류(계좌번호·형식)는 문구에 "오류"·"확인" 이 들어 있어서 그쪽은 실패로 둔다.
     */
    @Override
    public boolean hasPayload() {
        if (summary != null || (daily != null && !daily.isEmpty())) return true;
        String msg = rspMsg == null ? "" : rspMsg;
        return (msg.contains("없") || msg.contains("완료")) && !msg.contains("오류") && !msg.contains("확인");
    }

    /** 기간 합계 */
    public record Summary(
            @JsonProperty("fc_sll_amt_sum") BigDecimal sellAmountSum,
            @JsonProperty("fc_sdr_xps_sum") BigDecimal expenseSum,
            /** 실현손익 = 매매손익 - 제비용 */
            @JsonProperty("fc_rzt_pls_sum") BigDecimal realizedSum
    ) {
    }

    /** 주문일 하루 단위 손익. 종목 구분은 없다(상세 API 가 따로 있다) */
    public record Daily(
            @JsonProperty("orr_dt") String orderDate,
            @JsonProperty("sll_qty") BigDecimal sellQuantity,
            @JsonProperty("fc_sll_amt") BigDecimal sellAmount,
            @JsonProperty("fc_rzt_pls") BigDecimal realized
    ) {
    }

    /** 요청 본문 */
    public record Request(@JsonProperty("Input_0") Input input) {

        public record Input(
                @JsonProperty("act_no") String accountNo,
                /** 1 거래통화기준, 2 원화기준 */
                @JsonProperty("iqr_dit") String inquiryDivision,
                @JsonProperty("sta_orr_dt") String fromDate,
                @JsonProperty("end_orr_dt") String toDate,
                /** 비우면 전 종목 */
                @JsonProperty("iem_cd") String symbol,
                @JsonProperty("trd_cur_cd") String currency,
                /** 000 전체 국가 */
                @JsonProperty("fc_sec_trd_nat_cd") String countryCode
        ) {
        }

        /** 전 종목·전 국가, 원화 기준. 날짜는 YYYYMMDD */
        public static Request krw(String accountNo, String fromYmd, String toYmd) {
            return new Request(new Input(accountNo, "2", fromYmd, toYmd, "", "USD", "000"));
        }
    }
}
