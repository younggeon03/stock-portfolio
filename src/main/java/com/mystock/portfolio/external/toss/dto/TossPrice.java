package com.mystock.portfolio.external.toss.dto;

import java.math.BigDecimal;

/**
 * GET /api/v1/prices 의 현재가 한 건.
 *
 * 보유 중인 종목의 현재가는 이미 holdings 응답에 들어있어서 따로 부를 필요가 없다.
 * 이 API 는 "아직 안 샀지만 목표 비중을 정해둔 종목" 의 가격을 알아낼 때 쓴다.
 * (얼마어치 사야 하는지 계산하려면 가격이 필요하니까)
 */
public record TossPrice(

        /** 종목 심볼 */
        String symbol,

        /** 데이터 시각. 체결이 없으면 null 일 수 있다 */
        String timestamp,

        /** 현재가 (거래 통화 기준) */
        BigDecimal lastPrice,

        /** KRW 또는 USD */
        String currency
) {
}
