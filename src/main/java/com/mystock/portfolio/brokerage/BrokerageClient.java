package com.mystock.portfolio.brokerage;

import java.util.List;

/**
 * "보유 종목을 가져올 수 있는 증권사" 를 나타내는 규약.
 *
 * ★ 이 인터페이스가 있으면 좋은 점
 * 스프링은 같은 인터페이스를 구현한 빈들을 List 로 한 번에 주입해준다.
 *
 *   public UnifiedPortfolioService(List<BrokerageClient> clients) { ... }
 *
 * 이렇게 받아두면 증권사를 추가해도 이 생성자는 그대로다.
 * 새 증권사 클래스에 @Component 만 붙이면 자동으로 목록에 끼어든다.
 *
 * ★ 주문 기능은 일부러 넣지 않았다.
 * 이 인터페이스에는 조회 메서드만 있다. 주문 메서드가 없으니 실수로도 주문을 낼 수 없다.
 */
public interface BrokerageClient {

    /** 어느 증권사인지 */
    Broker broker();

    /** 이 증권사에서 지금 쓸 수 있는 상태인지 (.env 에 키가 채워져 있는지) */
    boolean isConfigured();

    /**
     * 보유 종목 목록. 없으면 빈 목록.
     *
     * @param ownerKey 누구의 포트폴리오인지 구분하는 값.
     *                 직접 입력한 종목은 사람마다 다르므로 이 값으로 나눠 담는다.
     *                 토스·나무처럼 API 키가 곧 신분인 곳은 이 값을 무시한다.
     *                 (키 주인의 계좌만 조회되므로 구분할 필요가 없다)
     */
    List<BrokerageHolding> holdings(String ownerKey);
}
