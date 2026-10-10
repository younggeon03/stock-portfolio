package com.mystock.portfolio.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/** 바깥 API 커넥션 풀: 크기·지표·RestClient 에 실제로 끼워지는지. 네트워크는 쓰지 않는다 */
class HttpClientResilienceConfigTest {

    private final HttpClientResilienceConfig config = new HttpClientResilienceConfig();
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

    @Test
    void 풀은_호스트당_8개_전체_40개() throws Exception {
        try (PoolingHttpClientConnectionManager pool = config.externalConnectionManager(meters)) {
            assertThat(pool.getMaxTotal()).isEqualTo(40);
            assertThat(pool.getDefaultMaxPerRoute()).isEqualTo(8);
        }
    }

    @Test
    void 풀_사용량이_지표로_나간다() throws Exception {
        try (PoolingHttpClientConnectionManager pool = config.externalConnectionManager(meters)) {
            assertThat(meters.find("httpcomponents.httpclient.pool.total.max").tag("httpclient", "external-api").gauge())
                    .isNotNull();
            assertThat(meters.find("httpcomponents.httpclient.pool.total.connections").gauges()).isNotEmpty();
        }
    }

    @Test
    void 모든_RestClient_가_같은_풀_하나를_쓴다() throws Exception {
        try (PoolingHttpClientConnectionManager pool = config.externalConnectionManager(meters);
             var http = config.externalHttpClient(pool)) {
            var customizer = config.resilientRestClientCustomizer(http, config.circuitBreakerRegistry(meters), meters);
            RestClient.Builder a = RestClient.builder();
            RestClient.Builder b = RestClient.builder();
            customizer.customize(a);
            customizer.customize(b);
            Object fa = field(a, "requestFactory");
            assertThat(fa.getClass().getSimpleName()).isEqualTo("HttpComponentsClientHttpRequestFactory");
            assertThat(fa).isSameAs(field(b, "requestFactory"));
        }
    }

    private static Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }
}
