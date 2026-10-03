package com.mystock.portfolio.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 바깥 API 호출의 타임아웃·재시도·서킷 브레이커. 주입받은 RestClient.Builder 로 만든 모든 클라이언트에 걸린다.
 *
 * ★ 타임아웃이 가장 먼저다
 * 스프링 부트 3.3 의 기본 RestClient 는 타임아웃이 없다. 상대가 연결만 받아 두고 답을 안 주면
 * 그 요청(과 스레드)은 끝없이 기다린다. 13F 배치 스레드나 사용자 요청 스레드가 그렇게 묶이면
 * 에러도 안 나고 화면만 멈춘다. 연결 5초, 응답 30초로 끊는다(SEC 13F 보유표처럼 큰 파일도 30초면 충분했다).
 */
@Configuration
public class HttpClientResilienceConfig {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

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

    @Bean
    RestClientCustomizer resilientRestClientCustomizer(CircuitBreakerRegistry breakers, MeterRegistry meters) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(CONNECT_TIMEOUT)
                .withReadTimeout(READ_TIMEOUT);
        ResilientHttpInterceptor interceptor = new ResilientHttpInterceptor(breakers, meters);
        return builder -> builder
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .requestInterceptor(interceptor);
    }
}
