package com.mystock.portfolio.external.edgar;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * SEC EDGAR 전용 RestClient.
 *
 * 호스트가 둘(www.sec.gov 매핑 파일, data.sec.gov 재무)이라 baseUrl 을 박지 않는다.
 * User-Agent 는 SEC 가 요구하는 연락처라서 여기서 한 번만 붙인다.
 */
@Configuration
public class EdgarRestClientConfig {

    @Bean
    public RestClient edgarRestClient(EdgarProperties properties) {
        RestClient.Builder builder = RestClient.builder();
        if (properties.hasUserAgent()) {
            builder.defaultHeader("User-Agent", properties.userAgent());
        }
        return builder.build();
    }
}
