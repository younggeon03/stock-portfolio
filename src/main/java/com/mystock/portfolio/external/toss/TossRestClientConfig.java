package com.mystock.portfolio.external.toss;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 토스증권 Open API 전용 RestClient.
 *
 * 기존 publicDataRestClient(Yahoo/구글뉴스용)와 달리 호출 대상 호스트가 하나로 고정이므로
 * baseUrl 을 미리 박아둔다. 그래서 각 서비스는 "/oauth2/token" 같은 경로만 넘기면 된다.
 *
 * 빈 이름이 tossRestClient 로 두 개(publicDataRestClient 와) 공존하므로,
 * 주입받는 쪽에서는 파라미터 이름을 tossRestClient 로 맞추거나 @Qualifier 를 쓴다.
 */
@Configuration
public class TossRestClientConfig {

    @Bean
    public RestClient tossRestClient(TossApiProperties properties, RestClient.Builder builder) {
        // 주입받은 Builder 라야 호출 지표가 남는다(HttpClientObservationConfig)
        return builder
                .baseUrl(properties.baseUrl())
                .build();
    }
}
