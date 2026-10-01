package com.mystock.portfolio.external.toss.dto;

import java.math.BigDecimal;

/**
 * 일봉(또는 분봉) 하나. GET /api/v1/candles 의 result.candles 배열 원소.
 *
 * OHLCV 는 주식 차트의 기본 단위다.
 *   Open  시가   그날 처음 거래된 가격
 *   High  고가   그날 가장 높았던 가격
 *   Low   저가   그날 가장 낮았던 가격
 *   Close 종가   그날 마지막 거래 가격  ← 변동성 계산에 쓰는 값
 *   Volume 거래량
 *
 * ★ 정렬 주의: 토스는 최신 봉이 배열의 맨 앞에 온다 (timestamp 내림차순).
 * 변동성이나 차트는 과거 → 현재 순서가 필요해서 우리 쪽에서 뒤집어 쓴다.
 */
public record TossCandle(

        /** 봉 기준 시각. 일봉이면 해당 거래일 (예: "2026-09-11T00:00:00-04:00") */
        String timestamp,

        BigDecimal openPrice,
        BigDecimal highPrice,
        BigDecimal lowPrice,

        /** 종가. 가장 많이 쓰는 값 */
        BigDecimal closePrice,

        BigDecimal volume,

        /** KRW 또는 USD */
        String currency
) {
}
