package com.mystock.portfolio.service.symbol;

/**
 * 이름으로 종목을 찾은 결과.
 *
 * ★ 점수를 같이 돌려주는 이유
 * 스크린샷에서 읽은 글자는 완벽하지 않다. "현대차" 가 "헌대차" 로 읽힐 수도 있다.
 * 확실히 찾았는지 애매하게 찾았는지를 화면이 알아야, 애매한 줄만 노랗게 칠해서
 * 사용자에게 "이거 맞나요?" 하고 물어볼 수 있다.
 */
public record SymbolMatch(

        /** 찾은 종목코드. 못 찾았으면 null */
        String symbol,

        /** 찾은 종목명 */
        String name,

        /** KR 또는 US */
        String marketCountry,

        /** KRW 또는 USD */
        String currency,

        /** STOCK / ETF 등 */
        String securityType,

        /**
         * 얼마나 확실한지 0~100.
         *   100  이름이나 코드가 정확히 일치
         *   70~99 거의 확실
         *   40~69 애매함. 화면에서 확인을 받는 게 좋다
         *   0    못 찾음
         */
        int confidence
) {

    /** 이 점수 미만이면 사용자 확인이 필요하다고 본다 */
    public static final int NEEDS_REVIEW_BELOW = 70;

    /** 못 찾았을 때 */
    public static SymbolMatch notFound() {
        return new SymbolMatch(null, null, null, null, null, 0);
    }

    public boolean found() {
        return symbol != null;
    }

    /** 사용자가 눈으로 확인해야 하는지 */
    public boolean needsReview() {
        return !found() || confidence < NEEDS_REVIEW_BELOW;
    }
}
