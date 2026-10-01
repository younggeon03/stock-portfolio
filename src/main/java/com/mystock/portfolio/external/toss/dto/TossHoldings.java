package com.mystock.portfolio.external.toss.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * GET /api/v1/holdings 의 result 부분 전체.
 *
 * 맨 위쪽은 계좌 전체 요약이고, items 에 종목별 상세가 들어있다.
 *
 * ★ 주의: 요약 금액은 통화별로 따로 담겨 온다 (krw / usd).
 * 토스가 환율로 합쳐주지 않는다. "원화 7,200,000원 + 달러 1,500달러" 처럼 따로 준다.
 * 그래서 전체 자산 대비 비중을 구하려면 우리가 환율을 가져와서 직접 합쳐야 한다.
 *
 * 참고: 스프링부트는 기본적으로 "모르는 JSON 필드는 무시" 하도록 설정되어 있다.
 * 그래서 토스가 주는 필드 중 우리가 안 쓰는 것(dailyProfitLoss, cost 등)은 그냥 안 적어도 된다.
 */
public record TossHoldings(

        /** 투자 원금 (통화별) */
        Amount totalPurchaseAmount,

        /** 평가금액 요약 */
        Summary marketValue,

        /** 손익 요약 */
        ProfitSummary profitLoss,

        /** 보유 종목 목록. 없으면 빈 배열 */
        List<TossHoldingItem> items
) {

    /** 통화별 금액 묶음. 미국 주식이 없으면 usd 는 null 이다. */
    public record Amount(BigDecimal krw, BigDecimal usd) {
    }

    /** 평가금액 요약 */
    public record Summary(Amount amount, Amount amountAfterCost) {
    }

    /**
     * 손익 요약.
     * rate 는 토스가 자체 환율로 전부 원화 환산해서 계산한 값이라 통화 구분 없이 하나로 온다.
     */
    public record ProfitSummary(Amount amount, BigDecimal rate) {
    }
}
