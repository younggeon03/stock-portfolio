package com.mystock.portfolio.external.toss.dto;

import java.math.BigDecimal;

/**
 * GET /api/v1/exchange-rate?baseCurrency=USD&quoteCurrency=KRW 의 result.
 *
 * 미국 주식 평가금액(달러)을 원화로 바꿔서 국내 종목과 같이 비중을 계산하려고 쓴다.
 * rate 가 1380.5 면 1달러 = 1380.5원 이라는 뜻이다.
 *
 * 1분마다 갱신되는 "참고용 표시 환율" 이라 실제 매매 환율과는 조금 다를 수 있다.
 * 비중을 보는 용도로는 충분하다.
 */
public record TossExchangeRate(

        /** 기준 통화. 우리가 USD 로 요청하므로 USD */
        String baseCurrency,

        /** 표시 통화. 우리가 KRW 로 요청하므로 KRW */
        String quoteCurrency,

        /** 1 baseCurrency 가 몇 quoteCurrency 인지. 예: 1380.5 */
        BigDecimal rate,

        /** 은행간 매매기준율 */
        BigDecimal midRate
) {
}
