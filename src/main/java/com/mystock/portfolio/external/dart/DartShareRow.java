package com.mystock.portfolio.external.dart;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 주식의 총수 현황(stockTotqySttus) 응답의 한 줄. 보통주 / 우선주 / 합계 / 비고 네 줄이 온다.
 *
 * 숫자는 쉼표 붙은 문자열이고, 자기주식이 없으면 "-" 다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DartShareRow(
        @JsonProperty("rcept_no") String receiptNo,
        /** 보통주 / 우선주 / 합계 / 비고 */
        @JsonProperty("se") String kind,
        /** 발행주식 총수 */
        @JsonProperty("istc_totqy") String issued,
        /** 자기주식 수 */
        @JsonProperty("tesstk_co") String treasury,
        /** 유통주식 수 = 발행 − 자기주식 */
        @JsonProperty("distb_stock_co") String outstanding,
        /** 기준일. 2026-06-30 */
        @JsonProperty("stlm_dt") String asOf
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(String status, String message, List<DartShareRow> list) {
    }
}
