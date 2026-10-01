package com.mystock.portfolio.external.dart;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 단일회사 주요계정(fnlttSinglAcnt) 응답의 한 줄.
 *
 * 금액은 "333,605,938,000,000" 처럼 쉼표 붙은 문자열로 온다. 숫자 변환은 쓰는 쪽에서 한다.
 * 분기·반기 보고서의 손익은 thstrm_amount 가 "그 분기 3개월" 이고
 * thstrm_add_amount 가 "연초부터 누적" 이다. 둘을 헷갈리면 이익이 반토막 난다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DartAccountRow(
        @JsonProperty("rcept_no") String receiptNo,
        /** CFS 연결 / OFS 별도 */
        @JsonProperty("fs_div") String fsDiv,
        /** BS 재무상태표 / IS 손익계산서 */
        @JsonProperty("sj_div") String sjDiv,
        @JsonProperty("account_nm") String accountName,
        @JsonProperty("thstrm_nm") String thisTermName,
        @JsonProperty("thstrm_dt") String thisTermDate,
        @JsonProperty("thstrm_amount") String thisTermAmount,
        @JsonProperty("thstrm_add_amount") String thisTermCumulative,
        @JsonProperty("frmtrm_amount") String priorTermAmount,
        @JsonProperty("frmtrm_add_amount") String priorTermCumulative,
        @JsonProperty("bfefrmtrm_amount") String twoTermsAgoAmount
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(String status, String message, List<DartAccountRow> list) {
    }
}
