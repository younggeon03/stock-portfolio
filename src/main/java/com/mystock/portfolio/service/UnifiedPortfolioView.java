package com.mystock.portfolio.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 여러 증권사를 합친 포트폴리오 화면 데이터.
 *
 * 금액은 전부 원화 환산 기준이다.
 */
public record UnifiedPortfolioView(

        /** 조회 범위. ALL(전체 합산) / TOSS / NAMUH */
        String scope,

        /** 전체 평가금액 (원화) */
        BigDecimal totalValueKrw,

        /** 전체 매입금액 (원화) */
        BigDecimal totalPurchaseKrw,

        /** 전체 손익 (원화) */
        BigDecimal totalProfitLossKrw,

        /** 전체 수익률 (%) */
        BigDecimal totalProfitRatePercent,

        /** 증권사별 요약. 조회에 실패한 증권사도 여기에 이유와 함께 담긴다 */
        List<BrokerSummary> brokers,

        /** 종목별 (같은 종목은 증권사를 넘어 하나로 합쳐진다) */
        List<Item> items
) {

    /** 증권사 한 곳의 요약 */
    public record BrokerSummary(

            /** TOSS / NAMUH */
            String broker,

            /** 토스증권 / 나무증권 */
            String brokerName,

            /** .env 에 키가 설정되어 있는지 */
            boolean configured,

            /** 이 증권사의 평가금액 합계 (원화) */
            BigDecimal valueKrw,

            /** 전체 자산에서 이 증권사가 차지하는 비중 (%) */
            BigDecimal weightPercent,

            /** 보유 종목 수 */
            int itemCount,

            /** 조회 실패 시 이유. 정상이면 null */
            String error
    ) {
    }

    /** 종목 한 줄. 여러 증권사에 나눠 들고 있어도 한 줄로 합쳐진다 */
    public record Item(

            String symbol,

            String name,

            /** KR 또는 US */
            String marketCountry,

            /** KRW 또는 USD */
            String currency,

            /** 총 보유 수량 (증권사 합산) */
            BigDecimal quantity,

            /** 현재가 (거래 통화 기준) */
            BigDecimal lastPrice,

            /**
             * 내 매수 평균가 (거래 통화 기준).
             * 같은 종목을 두 증권사에 나눠 샀으면 수량 가중평균이다.
             *
             * 기업분석에서 "지금 가격이 내 평단가 대비 어디인가" 를 따질 때 쓴다.
             * 미국 주식은 달러 기준이라야 밸류에이션과 비교가 된다.
             */
            BigDecimal averagePurchasePrice,

            /** 평가금액 (원화, 증권사 합산) */
            BigDecimal marketValueKrw,

            /** 매입금액 (원화, 증권사 합산) */
            BigDecimal purchaseKrw,

            /** 손익 (원화) */
            BigDecimal profitLossKrw,

            /** 수익률 (%) */
            BigDecimal profitRatePercent,

            /** 전체 자산 대비 비중 (%) */
            BigDecimal weightPercent,

            /**
             * 증권사별 내역.
             * 같은 종목을 두 곳에 들고 있으면 여기에 두 줄이 들어간다.
             * "어느 계좌에서 사고팔지" 를 정할 때 필요하다.
             */
            List<Lot> lots
    ) {
    }

    /** 특정 증권사에 들어있는 물량 */
    public record Lot(

            String broker,

            String brokerName,

            BigDecimal quantity,

            BigDecimal marketValueKrw
    ) {
    }
}
