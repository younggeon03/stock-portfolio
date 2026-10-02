package com.mystock.portfolio.external.dart;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 공시 검색(list.json) 응답의 한 줄. 배당결정처럼 거래소 공시를 찾을 때 쓴다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DartDisclosureRow(
        /** 접수번호. 공시 본문을 받을 때 쓴다. 정정 공시일수록 크다 */
        @JsonProperty("rcept_no") String receiptNo,
        /** 예: "현금ㆍ현물배당결정", "[기재정정]현금ㆍ현물배당결정" */
        @JsonProperty("report_nm") String reportName,
        /** 접수일 20260730 */
        @JsonProperty("rcept_dt") String receiptDate,
        @JsonProperty("corp_name") String corpName,
        @JsonProperty("stock_code") String stockCode
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(String status, String message, List<DartDisclosureRow> list) {
    }
}
