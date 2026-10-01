package com.mystock.portfolio.external.namuh.dto;

/**
 * 나무증권 응답의 공통 부분.
 *
 * ★ 토스와 크게 다른 점
 * 토스는 실패하면 HTTP 상태코드가 4xx/5xx 로 내려와서 예외로 바로 잡힌다.
 * 나무는 **HTTP 200 을 주면서 본문 안의 rsp_cd 로 성공/실패를 알려주는** 경우가 있다.
 * 그래서 상태코드만 보면 안 되고 rsp_cd 를 반드시 확인해야 한다.
 *
 * 성공 코드는 "00000" 이다.
 *
 * 이 인터페이스를 각 응답 DTO 가 구현하면, NamuhApiClient 가 한 곳에서 성공 여부를 검사할 수 있다.
 */
public interface NamuhResponse {

    /** 응답 코드. "00000" 이면 성공 */
    String rspCd();

    /** 응답 메시지. 실패 원인이 한글로 들어온다 */
    String rspMsg();

    /** 대표 성공 코드 */
    String SUCCESS_CODE = "00000";

    /**
     * 코드만 놓고 봤을 때 성공인지.
     *
     * ★ 주의: 이것만으로 판단하면 안 된다.
     * 실제로 잔고 조회는 rsp_cd 가 "00166" 인데 메시지는 "조회가 완료되었습니다" 로 온다.
     * 즉 00000 이 아니어도 정상인 경우가 있다. TR 마다 성공 코드가 다른 것으로 보인다.
     */
    default boolean isSuccessCode() {
        return rspCd() == null || SUCCESS_CODE.equals(rspCd());
    }

    /**
     * 응답에 실제 데이터가 들어있는지.
     *
     * 성공 코드가 제각각이라서, "데이터가 왔으면 성공" 으로 보는 게 더 정확하다.
     * 진짜 오류일 때는 데이터가 비어 있다.
     * 각 응답 DTO 가 자기 상황에 맞게 구현한다.
     */
    boolean hasPayload();

    /** 최종 판정: 코드가 성공이거나, 데이터가 실제로 들어왔으면 성공 */
    default boolean isSuccess() {
        return isSuccessCode() || hasPayload();
    }
}
