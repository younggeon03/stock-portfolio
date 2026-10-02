package com.mystock.portfolio.web;

import com.mystock.portfolio.config.SecurityConfig;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 지금 로그인 상태. 화면이 로그아웃 버튼을 보일지 정하는 데만 쓴다.
 * 로그인이 꺼진 개발 모드(비밀번호 없음)에서는 로그아웃 버튼이 의미가 없어서 숨겨야 한다.
 */
@Tag(name = "로그인", description = "내 영역 로그인 상태")
@RestController
public class MeController {

    private final SecurityConfig securityConfig;

    public MeController(SecurityConfig securityConfig) {
        this.securityConfig = securityConfig;
    }

    @GetMapping("/api/me")
    public Map<String, Object> me(Authentication authentication) {
        boolean required = securityConfig.loginRequired();
        String name = required && authentication != null ? authentication.getName() : null;
        return Map.of("loginRequired", required, "username", name == null ? "" : name);
    }
}
