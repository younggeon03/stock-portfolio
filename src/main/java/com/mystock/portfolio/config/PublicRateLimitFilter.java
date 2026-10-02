package com.mystock.portfolio.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;

/**
 * 공개 API 요청 제한. 로그인 없이 열린 주소를 누가 반복 호출해 서버나 DART 한도를 바닥내지 못하게.
 *
 * - 공개 조회(/api/institutions/**, /api/public/**, /feeds/**): IP 당 분당 120번
 * - 배당 캘린더(/api/public/dividends): IP 당 분당 15번. 처음 보는 종목마다 DART 를 부른다(하루 2만 건 한도)
 *
 * 내 영역은 로그인 뒤라 여기서 세지 않는다. 화면 파일(/public/*.html 등)도 세지 않는다.
 * IP 는 앞단(Caddy)이 붙인 X-Forwarded-For 를 스프링이 반영한 값이다 (server.forward-headers-strategy).
 */
@Component
public class PublicRateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter general;
    private final RateLimiter dividends;

    public PublicRateLimitFilter(@Value("${rate-limit.public-per-minute:120}") int publicPerMinute,
                                 @Value("${rate-limit.dividends-per-minute:15}") int dividendsPerMinute) {
        this.general = new RateLimiter(publicPerMinute, Clock.systemUTC());
        this.dividends = new RateLimiter(dividendsPerMinute, Clock.systemUTC());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String p = request.getRequestURI();
        return !(p.startsWith("/api/institutions") || p.startsWith("/api/public/") || p.startsWith("/feeds/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String ip = request.getRemoteAddr();
        RateLimiter.Decision d = general.tryAcquire(ip);
        if (d.allowed() && request.getRequestURI().startsWith("/api/public/dividends")) {
            d = dividends.tryAcquire(ip);
        }
        if (!d.allowed()) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(d.retryAfterSeconds()));
            response.setContentType("application/json;charset=UTF-8");
            response.getOutputStream().write(("{\"error\":\"요청이 너무 많습니다. " + d.retryAfterSeconds()
                    + "초 뒤에 다시 시도해 주세요.\"}").getBytes(StandardCharsets.UTF_8));
            return;
        }
        chain.doFilter(request, response);
    }
}
