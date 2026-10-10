package com.mystock.portfolio.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.httpcomponents.hc5.PoolingHttpClientConnectionManagerMetricsBinder;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

import java.time.Duration;

/**
 * 바깥 API 호출의 연결(커넥션 풀·소켓)·타임아웃·재시도·서킷 브레이커. 주입받은 RestClient.Builder 로 만든 모든 클라이언트에 걸린다.
 *
 * ★ 타임아웃이 가장 먼저다
 * 스프링 부트 3.3 의 기본 RestClient 는 타임아웃이 없다. 상대가 연결만 받아 두고 답을 안 주면
 * 그 요청(과 스레드)은 끝없이 기다린다. 13F 배치 스레드나 사용자 요청 스레드가 그렇게 묶이면
 * 에러도 안 나고 화면만 멈춘다. 연결 5초, 응답 30초로 끊는다(SEC 13F 보유표처럼 큰 파일도 30초면 충분했다).
 *
 * ★ 커넥션 풀은 앱 전체에 하나
 * 예전에는 RestClient 를 만들 때마다 클라이언트(와 풀)가 따로 생겼고, 그 클라이언트는 Anthropic SDK 를 따라
 * 들어온 OkHttp 였다(아무도 고르지 않았다). 지금은 Apache HttpClient 5 풀 하나를 모든 RestClient 가 같이 쓴다.
 * - 같은 호스트는 연결을 다시 쓴다(keep-alive). 요청마다 TCP 3-way 핸드셰이크 + TLS 핸드셰이크를 하지 않는다
 * - 호스트당 8개, 전체 40개. 넘으면 최대 5초 기다리고 실패한다(무한정 쌓이지 않게)
 * - 30초 넘게 놀던 연결은 닫는다. 상대 서버가 먼저 끊은 연결을 다시 쓰다 "응답 없음" 으로 실패하는 일을 줄인다
 * - 5분 넘은 연결은 다시 맺는다. 상대의 IP(DNS)가 바뀌어도 오래된 연결에 묶여 있지 않게
 * - 풀 사용량은 httpcomponents_httpclient_pool_* 지표로 나간다
 */
@Configuration
public class HttpClientResilienceConfig {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    static final int MAX_CONNECTIONS_TOTAL = 40;
    static final int MAX_CONNECTIONS_PER_HOST = 8;
    /** 풀이 꽉 찼을 때 빈 연결을 기다리는 상한 */
    static final Duration POOL_WAIT = Duration.ofSeconds(5);
    static final Duration IDLE_EVICT = Duration.ofSeconds(30);
    static final Duration CONNECTION_TTL = Duration.ofMinutes(5);

    @Bean
    CircuitBreakerRegistry circuitBreakerRegistry(MeterRegistry meters) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(20)
                // 호출이 적은 호스트가 두세 번 실패로 열리지 않게 최소 호출 수를 둔다
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .build();
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        // resilience4j_circuitbreaker_state{name="호스트",state="open"} 등. 알림 CircuitBreakerOpen 이 이걸 본다
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry).bindTo(meters);
        return registry;
    }

    @Bean(destroyMethod = "close")
    PoolingHttpClientConnectionManager externalConnectionManager(MeterRegistry meters) {
        PoolingHttpClientConnectionManager pool = PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(MAX_CONNECTIONS_TOTAL)
                .setMaxConnPerRoute(MAX_CONNECTIONS_PER_HOST)
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(timeout(CONNECT_TIMEOUT))
                        .setSocketTimeout(timeout(READ_TIMEOUT))
                        .setTimeToLive(TimeValue.ofMilliseconds(CONNECTION_TTL.toMillis()))
                        // 2초 넘게 놀던 연결은 꺼내 쓰기 전에 살아 있는지 확인한다
                        .setValidateAfterInactivity(TimeValue.ofSeconds(2))
                        .build())
                .setDefaultSocketConfig(SocketConfig.custom()
                        // 요청 본문이 작아서 Nagle 이 모아 보내려고 기다릴 이유가 없다
                        .setTcpNoDelay(true)
                        .setSoTimeout(timeout(READ_TIMEOUT))
                        .build())
                .build();
        // httpcomponents_httpclient_pool_total_connections{state="leased|available"}, _pending 등
        new PoolingHttpClientConnectionManagerMetricsBinder(pool, "external-api").bindTo(meters);
        return pool;
    }

    @Bean(destroyMethod = "close")
    CloseableHttpClient externalHttpClient(PoolingHttpClientConnectionManager pool) {
        return HttpClients.custom()
                .setConnectionManager(pool)
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectionRequestTimeout(timeout(POOL_WAIT))
                        .setResponseTimeout(timeout(READ_TIMEOUT))
                        .build())
                .evictIdleConnections(TimeValue.ofMilliseconds(IDLE_EVICT.toMillis()))
                .evictExpiredConnections()
                // HttpClient 도 기본으로 한 번 다시 보낸다. 재시도는 ResilientHttpInterceptor 한 곳에서만 한다
                // (둘 다 켜 두면 3번이 6번이 되고, 서킷 브레이커가 실패를 덜 센다)
                .disableAutomaticRetries()
                .build();
    }

    @Bean
    RestClientCustomizer resilientRestClientCustomizer(CloseableHttpClient http, CircuitBreakerRegistry breakers,
                                                       MeterRegistry meters) {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(http);
        ResilientHttpInterceptor interceptor = new ResilientHttpInterceptor(breakers, meters);
        return builder -> builder
                .requestFactory(factory)
                .requestInterceptor(interceptor);
    }

    private static Timeout timeout(Duration d) {
        return Timeout.ofMilliseconds(d.toMillis());
    }
}
