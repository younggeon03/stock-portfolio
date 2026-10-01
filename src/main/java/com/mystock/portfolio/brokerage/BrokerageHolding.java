package com.mystock.portfolio.brokerage;

import java.math.BigDecimal;

/**
 * 증권사와 무관한 "보유 종목 한 건" 표준 모델.
 *
 * ★ 왜 이런 게 필요한가
 * 토스와 나무는 응답 모양이 완전히 다르다.
 *   토스: { "symbol": "NVDA", "marketValue": { "amount": "7.65" }, ... }  (문자열, 거래통화 기준)
 *   나무: { "iem_cd": "SOXL", "krw_eal_amt": 16381500, ... }          (숫자, 원화 환산 포함)
 *
 * 이걸 그대로 두고 계산하면 코드가 증권사마다 갈라진다.
 * 그래서 입구에서 이 모델로 통일해두고, 그 뒤 계산은 증권사를 모른 채 진행한다.
 *
 * ★ 금액 기준
 * marketValueKrw / purchaseAmountKrw 는 반드시 원화로 환산된 값이다.
 * 원화와 달러를 한 표에서 비교하려면 기준을 하나로 맞춰야 하기 때문이다.
 * lastPrice / averagePurchasePrice 는 원래 거래 통화 그대로 둔다 (화면에 $로 보여주려고).
 */
public record BrokerageHolding(

        /** 어느 증권사에서 가져온 것인지 */
        Broker broker,

        /** 종목코드. 국내는 6자리(005930), 미국은 티커(SOXL) */
        String symbol,

        /** 종목명 */
        String name,

        /** KR 또는 US */
        String marketCountry,

        /** KRW 또는 USD (원래 거래 통화) */
        String currency,

        /** 보유 수량 */
        BigDecimal quantity,

        /** 현재가 (거래 통화 기준) */
        BigDecimal lastPrice,

        /** 매수 평균가 (거래 통화 기준) */
        BigDecimal averagePurchasePrice,

        /** 평가금액 (원화 환산) */
        BigDecimal marketValueKrw,

        /** 매입금액 (원화 환산) */
        BigDecimal purchaseAmountKrw
) {

    /** 평가손익 (원화). 평가금액 − 매입금액 */
    public BigDecimal profitLossKrw() {
        return marketValueKrw.subtract(purchaseAmountKrw);
    }
}
