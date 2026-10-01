package com.mystock.portfolio.external.toss.dto;

import java.math.BigDecimal;

/**
 * GET /api/v1/stocks (종목 기본 정보 조회) 응답 한 건.
 *
 * ★ 이 API 는 재무 데이터를 주지 않는다.
 * 매출·PER·ROE 같은 건 없고, 트레이딩에 필요한 참조 데이터만 준다.
 * 그래도 기업분석에 아주 중요한 값이 세 개 들어있다.
 *
 *   securityType   → ETF 인지 일반 주식인지. ETF 에 PER/ROE 를 찾는 헛수고를 막아준다
 *   leverageFactor → 레버리지 배수. SOXL 은 3.0 이다. 위험 경고의 근거가 된다
 *   sharesOutstanding → 발행주식수. 현재가를 곱하면 시가총액이 나온다
 *
 * 이 값들은 "확실히 아는 사실" 이므로 LLM 에게 추측시키지 않고 그대로 넘겨준다.
 */
public record TossStockInfo(

        /** 종목 심볼. 국내 6자리(005930), 미국 티커(SOXL) */
        String symbol,

        /** 종목명 (한글) */
        String name,

        /** 영문 종목명. 해외 자료를 검색할 때 이 이름이 더 잘 맞는다 */
        String englishName,

        /** 국제증권식별번호 (ISO 6166) */
        String isinCode,

        /** 상장 시장. KOSPI / KOSDAQ / NYSE / NASDAQ / AMEX / KR_ETC / US_ETC */
        String market,

        /**
         * 종목 유형.
         * STOCK / FOREIGN_STOCK / DEPOSITARY_RECEIPT / INFRASTRUCTURE_FUND
         * / REIT / ETF / FOREIGN_ETF / ETN / STOCK_WARRANTS
         *
         * enum 이 아니라 String 인 이유: 토스가 새 유형을 추가해도 안 깨지게 하기 위해서다.
         */
        String securityType,

        /** 보통주 여부. 우선주면 false */
        Boolean isCommonShare,

        /** 상장 상태. SCHEDULED / ACTIVE / DELISTED */
        String status,

        /** 거래 통화. KRW 또는 USD */
        String currency,

        /** 상장일 (YYYY-MM-DD). 정보가 없으면 null */
        String listDate,

        /** 상장폐지일. 정상 종목은 null */
        String delistDate,

        /** 발행주식수. 현재가를 곱하면 시가총액 */
        BigDecimal sharesOutstanding,

        /**
         * 레버리지 배수. ETF/ETN 에만 값이 있다 (1.0, 2.0, 3.0, -1.0 등).
         * 일반 주식은 null.
         * 1 을 넘으면 위험 경고 대상이다.
         */
        BigDecimal leverageFactor,

        /** 국내 종목에만 오는 거래 상태 정보. 해외 종목은 null */
        KoreanMarketDetail koreanMarketDetail
) {

    /**
     * 국내 시장 상세 정보.
     *
     * 업종이나 섹터 정보는 없고 거래 상태 플래그뿐이다.
     * 하지만 정리매매나 거래정지는 그 자체로 심각한 위험 신호라 분석에 반드시 반영해야 한다.
     */
    public record KoreanMarketDetail(

            /** 정리매매 여부. true 면 상장폐지 절차가 진행 중이다 */
            Boolean liquidationTrading,

            /** NXT 대체거래소 지원 여부 */
            Boolean nxtSupported,

            /** KRX 거래정지 여부 */
            Boolean krxTradingSuspended,

            /** NXT 거래정지 여부. NXT 미지원 종목은 null */
            Boolean nxtTradingSuspended
    ) {
    }

    /** ETF·ETN·리츠처럼 재무제표를 따질 수 없는 상품인지 */
    public boolean isFund() {
        if (securityType == null) {
            return false;
        }
        return switch (securityType) {
            case "ETF", "FOREIGN_ETF", "ETN", "REIT", "INFRASTRUCTURE_FUND" -> true;
            default -> false;
        };
    }

    /** 레버리지 상품인지 (배수의 절대값이 1을 넘으면 레버리지/인버스) */
    public boolean isLeveraged() {
        return leverageFactor != null && leverageFactor.abs().compareTo(BigDecimal.ONE) > 0;
    }

    /** 정리매매 진행 중인지 */
    public boolean isLiquidationTrading() {
        return koreanMarketDetail != null && Boolean.TRUE.equals(koreanMarketDetail.liquidationTrading());
    }

    /** 거래정지 상태인지 */
    public boolean isTradingSuspended() {
        return koreanMarketDetail != null && Boolean.TRUE.equals(koreanMarketDetail.krxTradingSuspended());
    }

    /** 상장폐지되었거나 예정인지 */
    public boolean isDelisting() {
        return "DELISTED".equals(status) || (delistDate != null && !delistDate.isBlank());
    }
}
