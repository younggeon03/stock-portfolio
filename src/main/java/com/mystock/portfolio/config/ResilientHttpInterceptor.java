package com.mystock.portfolio.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 모든 바깥 API 호출에 거는 재시도 + 서킷 브레이커. RestClient 인터셉터라 클라이언트 코드는 모른다.
 *
 * ★ 재시도 규칙 (보수적으로)
 * - GET 만 다시 보낸다. POST(토큰 발급, 나무 조회 등)는 한 번 더 보내면 무슨 일이 생길지 상대 사정에 달려 있다
 * - 네트워크 오류(IOException)와 5xx 만. 4xx 는 다시 보내도 같다(토스 403 = 허용 IP, 404 = 없음)
 * - 429(호출 한도)도 다시 보내지 않는다. 바로 다시 두드리면 한도가 더 길게 막힌다
 * - 최대 3번, 0.5초 → 1초 대기에 흔들기(jitter)를 더한다. 여러 요청이 같은 순간에 몰려 다시 치지 않게
 *
 * ★ 서킷 브레이커 (호스트별)
 * 한 호스트가 계속 실패하면(최근 20번 중 절반, 최소 10번) 30초 동안 호출하지 않고 바로 실패시킨다.
 * 죽은 API 를 매번 타임아웃(30초)까지 기다리면 그동안 스레드가 묶이고 화면이 전부 느려진다.
 * 30초 뒤 몇 번만 시험 삼아 보내 보고 살아났으면 다시 연다.
 * 실패로 세는 것은 네트워크 오류와 5xx 뿐이다. 403 같은 4xx 로 열면 "허용 IP 를 등록하세요" 안내가 가려진다.
 */
public class ResilientHttpInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ResilientHttpInterceptor.class);

    static final int MAX_ATTEMPTS = 3;
    static final Duration FIRST_BACKOFF = Duration.ofMillis(500);

    private final CircuitBreakerRegistry breakers;
    private final MeterRegistry meters;
    /** 테스트에서 실제로 기다리지 않게 바꿀 수 있다 */
    private final Sleeper sleeper;

    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    public ResilientHttpInterceptor(CircuitBreakerRegistry breakers, MeterRegistry meters) {
        this(breakers, meters, Thread::sleep);
    }

    ResilientHttpInterceptor(CircuitBreakerRegistry breakers, MeterRegistry meters, Sleeper sleeper) {
        this.breakers = breakers;
        this.meters = meters;
        this.sleeper = sleeper;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String host = request.getURI().getHost() == null ? "unknown" : request.getURI().getHost();
        CircuitBreaker breaker = breakers.circuitBreaker(host);
        boolean retryable = HttpMethod.GET.equals(request.getMethod());

        for (int attempt = 1; ; attempt++) {
            if (!breaker.tryAcquirePermission()) {
                // RestClient 가 ResourceAccessException 으로 감싼다. 부르는 쪽의 실패 처리(fail-soft)를 그대로 탄다
                throw new IOException("바깥 API(" + host + ")가 계속 실패해 잠시 호출을 멈췄습니다 (서킷 브레이커). 잠시 뒤 다시 시도하세요");
            }
            long started = System.nanoTime();
            ClientHttpResponse response;
            try {
                response = execution.execute(request, body);
            } catch (IOException e) {
                breaker.onError(System.nanoTime() - started, TimeUnit.NANOSECONDS, e);
                if (!retryable || attempt >= MAX_ATTEMPTS) {
                    throw e;
                }
                retried(host, "io", attempt);
                backoff(attempt);
                continue;
            }

            int status = response.getStatusCode().value();
            if (status >= 500) {
                breaker.onError(System.nanoTime() - started, TimeUnit.NANOSECONDS, new IOException("HTTP " + status));
                if (retryable && attempt < MAX_ATTEMPTS) {
                    // 다시 보내기 전에 앞 응답을 닫는다. 안 닫으면 연결이 풀에 돌아가지 않는다
                    response.close();
                    retried(host, "5xx", attempt);
                    backoff(attempt);
                    continue;
                }
            } else {
                breaker.onSuccess(System.nanoTime() - started, TimeUnit.NANOSECONDS);
            }
            // 마지막 시도의 5xx 는 그대로 돌려준다. 부르는 쪽은 예전처럼 HttpServerErrorException 을 받는다
            return response;
        }
    }

    private void retried(String host, String reason, int attempt) {
        log.info("바깥 API {} {} — {}번째 다시 보냄", host, reason, attempt + 1);
        Counter.builder("http.client.retries")
                .description("바깥 API 재시도 횟수")
                .tag("client_name", host).tag("reason", reason)
                .register(meters).increment();
    }

    private void backoff(int attempt) throws IOException {
        long base = FIRST_BACKOFF.toMillis() << (attempt - 1);   // 500ms, 1000ms
        long jitter = ThreadLocalRandom.current().nextLong(base / 4 + 1);
        try {
            sleeper.sleep(base + jitter);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("재시도 대기 중 중단됨", e);
        }
    }
}
