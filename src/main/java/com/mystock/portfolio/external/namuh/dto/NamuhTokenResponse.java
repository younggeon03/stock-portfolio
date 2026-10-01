package com.mystock.portfolio.external.namuh.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * POST /oauth2/token 응답.
 *
 * 토스와 형태가 거의 같다. scope 필드가 하나 더 있을 뿐이다.
 *
 * {
 *   "access_token": "...",
 *   "scope": "oob",
 *   "token_type": "Bearer",
 *   "expires_in": 86400
 * }
 */
public record NamuhTokenResponse(

        @JsonProperty("access_token") String accessToken,

        /** 항상 "oob" */
        @JsonProperty("scope") String scope,

        /** 항상 "Bearer" */
        @JsonProperty("token_type") String tokenType,

        /** 만료까지 남은 초. 86400(24시간) */
        @JsonProperty("expires_in") long expiresIn
) {
}
