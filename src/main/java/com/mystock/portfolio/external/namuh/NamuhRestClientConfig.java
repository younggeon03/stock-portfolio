package com.mystock.portfolio.external.namuh;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 나무증권 전용 RestClient.
 *
 * 이 프로젝트에는 이제 RestClient 빈이 세 개다.
 *   publicDataRestClient : 공개 데이터(IP 조회 등)
 *   tossRestClient       : 토스증권
 *   namuhRestClient      : 나무증권
 *
 * 주입받는 쪽에서는 파라미터 이름을 빈 이름과 똑같이 맞추면 스프링이 알아서 골라준다.
 */
@Configuration
public class NamuhRestClientConfig {

    @Bean
    public RestClient namuhRestClient(NamuhApiProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .build();
    }
}
