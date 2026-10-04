package com.mystock.portfolio.external.thirteenf;

/**
 * 13F 배치가 한 번 끝났다는 알림. 새 제출을 받았거나 티커를 새로 찾았을 수 있다.
 *
 * 13F 조회 결과를 메모리에 들고 있는 쪽(InstitutionPortfolioService)이 이걸 받고 비운다.
 * 받은 게 없어도 낸다. 하루 한 번 캐시를 비우는 건 싸고, "바뀌었나" 를 잘못 판단해 묵은 값을 주는 것보다 낫다.
 */
public record ThirteenFDataChangedEvent(int newFilings, int resolvedCusips) {
}
