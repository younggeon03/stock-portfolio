package com.mystock.portfolio.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 화면에 뿌릴 포트폴리오 데이터 한 덩어리.
 *
 * 이 record 가 그대로 JSON 으로 바뀌어서 브라우저로 간다.
 * 즉 여기 적힌 필드 이름이 app.js 에서 쓰는 이름이 된다. (totalValueKrw → data.totalValueKrw)
 *
 * 금액은 전부 "원화로 환산한 값" 으로 통일했다.
 * 달러 종목과 원화 종목을 한 표에서 비교하려면 기준을 하나로 맞춰야 하기 때문이다.
 */
public record TossPortfolioView(

        /** 조회에 사용한 계좌 식별 키 */
        Long accountSeq,

        /** 전체 평가금액 (원화 환산 합계) */
        BigDecimal totalValueKrw,

        /** 전체 매입금액 (원화 환산 합계) */
        BigDecimal totalPurchaseKrw,

        /** 전체 손익 = 평가금액 - 매입금액 */
        BigDecimal totalProfitLossKrw,

        /** 전체 수익률 (%). 예: 10.77 */
        BigDecimal totalProfitRatePercent,

        /** 적용한 환율 (1달러 = ?원). 미국 주식이 없으면 null */
        BigDecimal usdKrwRate,

        /** 종목별 상세 */
        List<Item> items
) {

    /** 표의 한 줄에 해당하는 데이터 */
    public record Item(

            /** 종목코드 (005930, AAPL) */
            String symbol,

            /** 종목명 */
            String name,

            /** KR 또는 US */
            String marketCountry,

            /** KRW 또는 USD (원래 거래되는 통화) */
            String currency,

            /** 보유 수량 */
            BigDecimal quantity,

            /** 현재가 (원래 통화 기준. 달러 종목이면 달러) */
            BigDecimal lastPrice,

            /** 매수 평균가 (원래 통화 기준) */
            BigDecimal averagePurchasePrice,

            /** 평가금액 (원래 통화 기준) */
            BigDecimal marketValue,

            /** 평가금액 (원화 환산). 비중 계산과 합계에 쓰는 값 */
            BigDecimal marketValueKrw,

            /** 손익 금액 (원화 환산) */
            BigDecimal profitLossKrw,

            /** 수익률 (%). 예: 10.77 */
            BigDecimal profitRatePercent,

            /** 이 종목이 전체 자산에서 차지하는 비중 (%). 예: 45.32 */
            BigDecimal weightPercent
    ) {
    }
}
