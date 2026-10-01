package com.mystock.portfolio.external.dart;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * DART OpenAPI 전용 RestClient. 토스와 같은 이유로 호스트를 미리 박아둔다.
 * 주입받는 쪽은 파라미터 이름을 dartRestClient 로 맞춘다.
 */
@Configuration
public class DartRestClientConfig {

    @Bean
    public RestClient dartRestClient(DartProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .build();
    }
}
