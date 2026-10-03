package com.mystock.portfolio.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 바깥 API 재시도·서킷 브레이커. 실제로 바깥을 부르지 않고 응답을 차례로 흉내 낸다 */
class ResilientHttpInterceptorTest {

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final CircuitBreakerRegistry breakers = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
            .slidingWindowSize(20).minimumNumberOfCalls(10).failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(30)).build());
    /** 기다리지 않는 재시도 */
    private final ResilientHttpInterceptor interceptor = new ResilientHttpInterceptor(breakers, meters, ms -> { });

    /** 미리 넣어 둔 결과(응답 또는 예외)를 순서대로 내놓는 가짜 실행기 */
    private static final class Script implements ClientHttpRequestExecution {
        final Deque<Object> results = new ArrayDeque<>();
        int calls;

        Script then(Object r) {
            results.add(r);
            return this;
        }

        @Override
        public ClientHttpResponse execute(org.springframework.http.HttpRequest request, byte[] body) throws IOException {
            calls++;
            Object r = results.isEmpty() ? new MockClientHttpResponse(new byte[0], HttpStatus.OK) : results.poll();
            if (r instanceof IOException e) {
                throw e;
            }
            return (ClientHttpResponse) r;
        }
    }

    private static MockClientHttpRequest req(HttpMethod method) {
        return new MockClientHttpRequest(method, URI.create("https://data.sec.gov/api/x"));
    }

    private static MockClientHttpResponse status(HttpStatus s) {
        return new MockClientHttpResponse(new byte[0], s);
    }

    private double retries() {
        var c = meters.find("http.client.retries").counters();
        return c.stream().mapToDouble(x -> x.count()).sum();
    }

    @Test
    void GET_이_5xx_면_다시_보내고_성공을_돌려준다() throws IOException {
        Script s = new Script().then(status(HttpStatus.SERVICE_UNAVAILABLE)).then(status(HttpStatus.OK));

        ClientHttpResponse res = interceptor.intercept(req(HttpMethod.GET), new byte[0], s);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(s.calls).isEqualTo(2);
        assertThat(retries()).isEqualTo(1);
    }

    @Test
    void 끝까지_5xx_면_마지막_응답을_그대로_돌려준다() throws IOException {
        Script s = new Script().then(status(HttpStatus.BAD_GATEWAY)).then(status(HttpStatus.BAD_GATEWAY)).then(status(HttpStatus.BAD_GATEWAY));

        ClientHttpResponse res = interceptor.intercept(req(HttpMethod.GET), new byte[0], s);

        assertThat(res.getStatusCode().value()).isEqualTo(502);   // 부르는 쪽은 예전처럼 HttpServerErrorException
        assertThat(s.calls).isEqualTo(ResilientHttpInterceptor.MAX_ATTEMPTS);
    }

    @Test
    void POST_는_다시_보내지_않는다() throws IOException {
        Script s = new Script().then(status(HttpStatus.SERVICE_UNAVAILABLE));

        ClientHttpResponse res = interceptor.intercept(req(HttpMethod.POST), new byte[0], s);

        assertThat(res.getStatusCode().value()).isEqualTo(503);
        assertThat(s.calls).isEqualTo(1);
    }

    /** 403(허용 IP)·404·429 는 다시 보내도 같거나 더 나빠진다 */
    @Test
    void 클라이언트_오류_4xx_는_다시_보내지_않는다() throws IOException {
        for (HttpStatus st : new HttpStatus[]{HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND, HttpStatus.TOO_MANY_REQUESTS}) {
            Script s = new Script().then(status(st));
            assertThat(interceptor.intercept(req(HttpMethod.GET), new byte[0], s).getStatusCode()).isEqualTo(st);
            assertThat(s.calls).isEqualTo(1);
        }
    }

    @Test
    void 네트워크_오류는_세_번까지_보내고_실패를_던진다() {
        Script s = new Script().then(new IOException("연결 끊김")).then(new IOException("연결 끊김")).then(new IOException("연결 끊김"));

        assertThatThrownBy(() -> interceptor.intercept(req(HttpMethod.GET), new byte[0], s)).hasMessage("연결 끊김");
        assertThat(s.calls).isEqualTo(3);
    }

    @Test
    void 계속_실패하면_회로가_열려_바로_실패한다() throws IOException {
        // POST 로 재시도 없이 실패 10번(최소 호출 수) → 실패율 100%
        for (int i = 0; i < 10; i++) {
            interceptor.intercept(req(HttpMethod.POST), new byte[0], new Script().then(status(HttpStatus.INTERNAL_SERVER_ERROR)));
        }
        assertThat(breakers.circuitBreaker("data.sec.gov").getState()).isEqualTo(CircuitBreaker.State.OPEN);

        Script s = new Script();
        assertThatThrownBy(() -> interceptor.intercept(req(HttpMethod.GET), new byte[0], s))
                .isInstanceOf(IOException.class).hasMessageContaining("서킷 브레이커");
        assertThat(s.calls).isZero();   // 상대를 부르지도 않는다
    }

    @Test
    void 클라이언트_오류_4xx_는_회로를_열지_않는다() throws IOException {
        for (int i = 0; i < 15; i++) {
            interceptor.intercept(req(HttpMethod.GET), new byte[0], new Script().then(status(HttpStatus.FORBIDDEN)));
        }
        assertThat(breakers.circuitBreaker("data.sec.gov").getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}
