package com.mystock.portfolio.external.toss.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * GET /api/v1/orders?status=CLOSED 의 result. 끝난 주문(체결·취소·거부) 목록을 페이지로 준다.
 *
 * 해외주식 실현손익 계산에만 쓴다. 주문을 내는 쪽 API 는 이 앱에 없다(결정기록 001).
 *
 * ★ 이 목록에 안 나오는 주문이 있다
 * 토스 문서: Open API 로 낼 수 없는 호가 유형(장전·장후 시간외 종가 등)의 주문은 목록에도 안 나온다.
 * 그래서 매수 기록이 빠질 수 있고, 계산하는 쪽이 그걸 알려야 한다(FifoGainCalculator 의 notes).
 */
public record TossOrderPage(List<Order> orders, String nextCursor, boolean hasNext) {

    public record Order(
            String orderId,
            String symbol,
            /** BUY / SELL */
            String side,
            /** 거래 통화. 미국 주식이면 USD */
            String currency,
            String status,
            String orderedAt,
            Execution execution
    ) {
    }

    /** 체결 결과. 금액은 거래 통화(달러) */
    public record Execution(
            BigDecimal filledQuantity,
            BigDecimal averageFilledPrice,
            BigDecimal filledAmount,
            BigDecimal commission,
            BigDecimal tax,
            String filledAt,
            /** 결제일. 양도소득은 이 날짜의 연도와 환율로 잡는다 */
            LocalDate settlementDate
    ) {
    }
}
