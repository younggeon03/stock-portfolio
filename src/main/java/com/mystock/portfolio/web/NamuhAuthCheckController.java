package com.mystock.portfolio.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.mystock.portfolio.external.namuh.NamuhAuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 나무증권 인증 동작 확인용.
 *
 * http://localhost:8080/api/namuh/auth/check 를 열어서
 * .env 의 appkey/appsecretkey 로 토큰이 실제로 나오는지 확인한다.
 *
 * 토큰 전체 값은 절대 내보내지 않는다. 앞 10글자만 보여준다.
 */
@Tag(name = "나무증권 점검", description = "토큰이 제대로 발급되는지 확인한다")
@RestController
@RequestMapping("/api/namuh")
public class NamuhAuthCheckController {

    private final NamuhAuthService namuhAuthService;

    public NamuhAuthCheckController(NamuhAuthService namuhAuthService) {
        this.namuhAuthService = namuhAuthService;
    }

    @GetMapping("/auth/check")
    public Map<String, Object> check() {
        String accessToken = namuhAuthService.getAccessToken();
        Instant expiresAt = namuhAuthService.currentTokenExpiresAt();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("message", "나무증권 토큰 발급 성공");
        result.put("tokenPreview", maskToken(accessToken));
        result.put("expiresAt", expiresAt);
        result.put("secondsUntilExpiry",
                expiresAt == null ? null : Duration.between(Instant.now(), expiresAt).toSeconds());
        return result;
    }

    private String maskToken(String token) {
        if (token == null || token.length() <= 10) {
            return "****";
        }
        return token.substring(0, 10) + "...(총 " + token.length() + "자)";
    }
}
