package com.mystock.portfolio.external.toss.dto;

/**
 * 토스 API 의 공통 응답 껍데기.
 *
 * 토큰 발급을 뺀 모든 토스 API 는 실제 데이터를 "result" 안에 한 겹 넣어서 준다.
 *
 *   { "result": { ...진짜 데이터... } }
 *
 * 매번 "XxxResponse(List<Xxx> result)" 같은 클래스를 만들기 귀찮으니
 * 제네릭(T)으로 한 번만 만들어 두고 돌려쓴다.
 *
 * 쓰는 쪽 예시:
 *   TossApiEnvelope<List<TossAccount>>   → 계좌 목록
 *   TossApiEnvelope<TossHoldings>        → 보유 주식
 *
 * 참고: 실패하면 result 대신 {"error": ...} 가 오는데, 그때는 HTTP 상태코드도 4xx/5xx 라서
 * RestClient 가 예외를 던진다. 그래서 여기서 error 를 따로 담을 필요는 없다.
 */
public record TossApiEnvelope<T>(T result) {
}
