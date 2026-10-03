package com.mystock.portfolio.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.observation.ClientRequestObservationConvention;

/**
 * 바깥 API 호출을 지표로 남긴다.
 *
 * 스프링 부트가 자동 구성한 RestClient.Builder 를 거쳐 만든 클라이언트만 관측된다.
 * 예전에는 클라이언트마다 정적 RestClient.builder() 로 만들어 이 연결을 건너뛰었고,
 * 그래서 Grafana 의 "외부 API 실패" 칸을 로그 경고 수로 대신 채웠다. 지금은 전부 주입받은 Builder 를 쓴다.
 *
 * 부트는 이 타입의 빈이 있으면 기본 규칙 대신 이걸 쓴다(RestClientObservationConfiguration).
 */
@Configuration
public class HttpClientObservationConfig {

    @Bean
    ClientRequestObservationConvention safeClientRequestObservationConvention() {
        return new SafeClientRequestObservationConvention();
    }
}
