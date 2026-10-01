package com.mystock.portfolio.external.toss.dto;

import java.math.BigDecimal;

/**
 * 보유 종목 한 건. (GET /api/v1/holdings 의 result.items 배열 원소)
 *
 * ★ 왜 전부 BigDecimal 인가
 * 토스는 금액/수량을 JSON 숫자가 아니라 문자열로 준다. 예: "lastPrice": "72000"
 * double 로 받으면 0.1 + 0.2 = 0.30000000000000004 같은 오차가 생겨서 돈 계산에 쓰면 안 된다.
 * BigDecimal 로 받으면 문자열을 Jackson 이 그대로 정확한 숫자로 바꿔준다.
 *
 * ★ 모든 금액은 "거래 통화 기준" 이다
 * 미국 주식이면 달러 금액이 들어온다. 원화 종목과 그냥 더하면 안 되고 환율로 환산해야 한다.
 * 환산은 TossPortfolioService 에서 한다.
 */
public record TossHoldingItem(

        /** 종목 심볼. 국내는 6자리 코드(005930), 미국은 티커(AAPL) */
        String symbol,

        /** 종목명 (삼성전자) */
        String name,

        /** KR 또는 US. 새 값이 추가돼도 안 깨지도록 enum 대신 String 으로 받는다 */
        String marketCountry,

        /** KRW 또는 USD */
        String currency,

        /** 보유 수량 */
        BigDecimal quantity,

        /** 현재가 (거래 통화 기준) */
        BigDecimal lastPrice,

        /** 내 매수 평균가 (거래 통화 기준) */
        BigDecimal averagePurchasePrice,

        /** 평가금액 묶음 */
        MarketValue marketValue,

        /** 손익 묶음 */
        ProfitLoss profitLoss
) {

    /** 평가금액. 토스 응답에서 marketValue 아래에 들어있는 값들 */
    public record MarketValue(

            /** 내가 산 총액 (수량 × 평균단가) */
            BigDecimal purchaseAmount,

            /** 지금 시세로 계산한 평가금액 */
            BigDecimal amount,

            /** 지금 당장 팔았을 때 세금/수수료 떼고 남는 금액 */
            BigDecimal amountAfterCost
    ) {
    }

    /** 손익. rate 는 퍼센트가 아니라 소수다. 0.1077 = 10.77% */
    public record ProfitLoss(

            /** 손익 금액 */
            BigDecimal amount,

            /** 세금/수수료 뺀 손익 금액 */
            BigDecimal amountAfterCost,

            /** 손익률 (소수). 화면에 % 로 보여주려면 100 을 곱해야 한다 */
            BigDecimal rate,

            /** 세금/수수료 뺀 손익률 (소수) */
            BigDecimal rateAfterCost
    ) {
    }
}
