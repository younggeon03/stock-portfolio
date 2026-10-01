package com.mystock.portfolio.external.toss.dto;

/**
 * GET /api/v1/stocks/all (마켓별 전체 종목 조회) 응답 한 건.
 *
 * 종목 마스터를 채우는 데 쓴다.
 * 스크린샷에서 읽은 "현대차" 를 실제 코드 005380 으로 바꾸려면 이 표가 필요하다.
 */
public record TossListedStock(

        /** 종목코드 */
        String symbol,

        /** 종목명 (한글) */
        String name,

        /** STOCK / ETF / ETN 등 */
        String securityType,

        /** 보통주 여부 */
        Boolean isCommonShare,

        /** ISIN 코드 */
        String isinCode
) {
}
