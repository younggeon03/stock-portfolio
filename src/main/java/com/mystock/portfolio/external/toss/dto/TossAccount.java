package com.mystock.portfolio.external.toss.dto;

/**
 * GET /api/v1/accounts 의 계좌 한 건.
 *
 * accountSeq 가 핵심이다. 보유주식/주문 등 "내 계좌" 관련 API 를 부를 때
 * X-Tossinvest-Account 헤더에 이 값을 넣어야 한다.
 *
 * accountType 을 enum 이 아니라 String 으로 받는 이유:
 * 토스 문서가 "클라이언트는 모르는 enum 값도 허용하도록 구현하라" 고 명시하고 있다.
 * enum 으로 받으면 토스가 새 계좌 유형을 추가하는 순간 파싱이 터진다.
 */
public record TossAccount(

        /** 계좌번호 (화면 표시용) */
        String accountNo,

        /** 계좌 식별 키. X-Tossinvest-Account 헤더에 넣는 값 */
        Long accountSeq,

        /** BROKERAGE(종합매매) 등. 현재는 BROKERAGE 만 내려온다 */
        String accountType
) {
}
