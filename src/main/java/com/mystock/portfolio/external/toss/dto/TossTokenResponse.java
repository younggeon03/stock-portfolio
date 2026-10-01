package com.mystock.portfolio.external.toss.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * POST /oauth2/token 의 응답.
 *
 * 주의: 이 응답만 OAuth2 표준 형식이라 필드명이 snake_case 다.
 * (보유주식/시세 등 나머지 토스 API 는 camelCase 이고 {"result": ...} 래퍼로 한 겹 감싸져 있다)
 *
 * 예시:
 * {
 *   "access_token": "eyJraWQiOi...",
 *   "token_type": "Bearer",
 *   "expires_in": 86400
 * }
 */
public record TossTokenResponse(

        /** 이후 모든 API 의 Authorization: Bearer {이 값} 헤더에 사용 */
        @JsonProperty("access_token") String accessToken,

        /** 항상 "Bearer" */
        @JsonProperty("token_type") String tokenType,

        /** 만료까지 남은 초. 보통 86400(24시간) */
        @JsonProperty("expires_in") long expiresIn
) {
}
