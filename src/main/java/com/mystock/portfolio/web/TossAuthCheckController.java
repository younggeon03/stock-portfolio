package com.mystock.portfolio.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.mystock.portfolio.external.toss.TossAuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 1단계(인증 모듈) 동작 확인용 엔드포인트.
 *
 * 브라우저에서 http://localhost:8080/api/toss/auth/check 를 열어보면
 * .env 의 client_id/secret 으로 토큰이 실제로 발급되는지 바로 알 수 있다.
 *
 * 보안: 토큰 전체 값은 절대 내려보내지 않는다. 앞 10글자만 보여줘서 "뭔가 받긴 받았다" 만 확인한다.
 * 실패하면 GlobalExceptionHandler 가 {"error": "..."} 형태로 이유를 내려준다.
 */
@Tag(name = "토스증권 점검", description = "토큰이 제대로 발급되는지 확인한다")
@RestController
@RequestMapping("/api/toss")
public class TossAuthCheckController {

    private final TossAuthService tossAuthService;

    public TossAuthCheckController(TossAuthService tossAuthService) {
        this.tossAuthService = tossAuthService;
    }

    @GetMapping("/auth/check")
    public Map<String, Object> check() {
        String accessToken = tossAuthService.getAccessToken();
        Instant expiresAt = tossAuthService.currentTokenExpiresAt();

        // LinkedHashMap 을 쓰면 JSON 에 넣은 순서대로 출력돼서 눈으로 보기 편하다.
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("message", "토큰 발급 성공");
        result.put("tokenPreview", maskToken(accessToken));
        result.put("expiresAt", expiresAt);
        result.put("secondsUntilExpiry", expiresAt == null ? null : Duration.between(Instant.now(), expiresAt).toSeconds());
        return result;
    }

    /** 토큰 앞부분만 남기고 가린다. */
    private String maskToken(String token) {
        if (token == null || token.length() <= 10) {
            return "****";
        }
        return token.substring(0, 10) + "...(총 " + token.length() + "자)";
    }
}
