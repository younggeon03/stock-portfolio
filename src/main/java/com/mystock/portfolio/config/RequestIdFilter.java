package com.mystock.portfolio.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 요청마다 번호(요청 ID)를 붙여 그 요청이 남긴 로그 줄을 한 번에 모을 수 있게 한다.
 *
 * ★ 왜 필요한가
 * 요청 여러 개가 동시에 들어오면 로그 줄이 뒤섞인다. "토스 503" 한 줄을 보고 그게 어느 화면의 어느 요청이었는지
 * 알려면 같은 번호로 묶여 있어야 한다. 번호는 MDC(로그 문맥)에 넣어 이 스레드가 남기는 모든 줄에 자동으로 붙고,
 * 응답 헤더(X-Request-Id)로도 돌려준다. 화면 오류를 받은 사람이 그 번호로 서버 로그를 찾는다.
 *
 * ★ 앞단이 준 번호를 이어 쓴다
 * 서버에서는 Caddy 가 요청마다 UUID 를 붙여 넘긴다(Caddyfile). 같은 번호를 쓰면 프록시와 앱 로그를 잇는다.
 * 바깥 사람이 아무 글자나 넣어 로그를 더럽히지 못하게 모양(영숫자·하이픈 8~64자)이 맞을 때만 받는다.
 *
 * 다른 필터(보안·요청 제한)보다 먼저 돌아야 그쪽 로그에도 번호가 붙는다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    /** 로그 패턴·JSON 필드 이름. logback-spring.xml 과 같아야 한다 */
    public static final String MDC_KEY = "requestId";

    private static final Logger access = LoggerFactory.getLogger("access");
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9-]{8,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = accept(request.getHeader(HEADER));
        MDC.put(MDC_KEY, id);
        response.setHeader(HEADER, id);
        long started = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            // API 만 한 줄씩 남긴다. 화면 파일(css·js)까지 남기면 로그가 그걸로 가득 찬다.
            // 쿼리스트링은 뺀다. 종목 목록 정도지만 무엇이 들어올지 모르는 값을 로그에 쌓지 않는다
            if (request.getRequestURI().startsWith("/api/") || request.getRequestURI().startsWith("/feeds/")) {
                access.info("{} {} {} {}ms", request.getMethod(), request.getRequestURI(), response.getStatus(),
                        (System.nanoTime() - started) / 1_000_000);
            }
            // 스레드는 풀로 재사용된다. 지우지 않으면 다음 요청이 이 번호를 물려받는다
            MDC.remove(MDC_KEY);
        }
    }

    /** 앞단이 준 번호가 안전한 모양이면 쓰고, 아니면 새로 만든다 */
    static String accept(String given) {
        if (given != null && SAFE.matcher(given).matches()) {
            return given;
        }
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
