package com.mystock.portfolio.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Yahoo Finance(시세), Google News RSS(뉴스)처럼 인증이 필요 없는 공개 데이터 소스를 호출할 때 쓰는
 * 범용 RestClient. 호스트가 호출마다 다르므로 baseUrl 을 고정하지 않고 매번 전체 URL 을 넘긴다.
 * 일부 공개 API 는 브라우저 User-Agent 가 없으면 차단하므로 기본 헤더로 넣어둔다.
 */
@Configuration
public class PublicDataRestClientConfig {

    @Bean
    public RestClient publicDataRestClient(RestClient.Builder builder) {
        // 주입받은 Builder 라야 호출 지표가 남는다(HttpClientObservationConfig). 부트는 주입할 때마다 새 Builder 를 준다
        return builder
                .defaultHeader("User-Agent", "Mozilla/5.0 (compatible; PortfolioApp/1.0)")
                .build();
    }
}
