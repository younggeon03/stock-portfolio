package com.mystock.portfolio.brokerage;

/**
 * 우리가 연동한 증권사.
 *
 * 증권사가 늘어나면 여기에 하나 추가하고 BrokerageClient 구현체를 하나 더 만들면 된다.
 * 계산 로직(비중, 리밸런싱)은 증권사를 전혀 모르므로 손댈 필요가 없다.
 */
public enum Broker {

    TOSS("토스증권"),
    NAMUH("나무증권"),

    /**
     * 직접 입력한 보유종목.
     *
     * ★ 왜 증권사 목록에 섞어놨는가
     * 토스·나무 API 키가 없는 사람도 이 앱을 쓸 수 있어야 하기 때문이다.
     * 타인 계좌를 조회하는 방법은 없으므로(증권사 API 가 그 기능을 제공하지 않는다),
     * 보유종목만 직접 받고 나머지 분석은 전부 앱이 제공한다.
     *
     * 시세·일봉·종목정보·환율은 계좌와 무관해서 앱의 키 하나로 어떤 종목이든 조회된다.
     * 그래서 직접 입력한 종목도 자동 연동된 종목과 똑같이 분석된다.
     */
    MANUAL("직접 입력");

    private final String displayName;

    Broker(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
