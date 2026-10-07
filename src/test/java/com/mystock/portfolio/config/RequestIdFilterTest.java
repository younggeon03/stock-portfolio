package com.mystock.portfolio.config;

import com.mystock.portfolio.common.LogContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** 요청 ID 가 로그 문맥·응답 헤더·백그라운드 스레드까지 따라가는지 */
class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    void 앞단이_준_번호를_이어_쓰고_응답_헤더로_돌려준다() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/public/analysis/MSFT");
        req.addHeader("X-Request-Id", "0f8fad5b-d9cb-469f-a165-70867728950e");
        MockHttpServletResponse res = new MockHttpServletResponse();
        AtomicReference<String> seen = new AtomicReference<>();

        filter.doFilter(req, res, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest r, jakarta.servlet.ServletResponse s) {
                seen.set(MDC.get("requestId"));   // 요청을 처리하는 동안 로그 문맥에 들어 있다
            }
        });

        assertThat(seen.get()).isEqualTo("0f8fad5b-d9cb-469f-a165-70867728950e");
        assertThat(res.getHeader("X-Request-Id")).isEqualTo("0f8fad5b-d9cb-469f-a165-70867728950e");
        assertThat(MDC.get("requestId")).isNull();   // 끝나면 지운다. 스레드를 물려받는 다음 요청에 남지 않게
    }

    @Test
    void 이상한_모양의_번호는_받지_않고_새로_만든다() {
        assertThat(RequestIdFilter.accept("abc\n가짜 로그 줄")).matches("[0-9a-f]{16}");
        assertThat(RequestIdFilter.accept("short")).matches("[0-9a-f]{16}");
        assertThat(RequestIdFilter.accept(null)).matches("[0-9a-f]{16}");
        assertThat(RequestIdFilter.accept("a".repeat(65))).hasSize(16);
    }

    @Test
    void 백그라운드로_넘긴_일에도_번호가_따라간다() throws Exception {
        MDC.put("requestId", "req-12345678");
        AtomicReference<String> inWorker = new AtomicReference<>();
        Runnable task = LogContext.carry(() -> inWorker.set(MDC.get("requestId")));
        MDC.clear();   // 요청 스레드는 이미 끝났다

        Thread t = new Thread(task);
        t.start();
        t.join();

        assertThat(inWorker.get()).isEqualTo("req-12345678");
    }

    @Test
    void 배치는_이름_붙은_새_번호를_달고_끝나면_치운다() {
        AtomicReference<String> inJob = new AtomicReference<>();
        LogContext.job("batch-13f", () -> inJob.set(MDC.get("requestId"))).run();

        assertThat(inJob.get()).startsWith("batch-13f-");
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void 로그_모양은_대소문자를_가리지_않고_모르는_값이면_줄글() {
        assertThat(LogFormatFilter.normalize("JSON")).isEqualTo("json");
        assertThat(LogFormatFilter.normalize(" json ")).isEqualTo("json");
        assertThat(LogFormatFilter.normalize("yaml")).isEqualTo("text");   // 로그가 통째로 사라지지 않게
        assertThat(LogFormatFilter.normalize(null)).isEqualTo("text");
    }
}
