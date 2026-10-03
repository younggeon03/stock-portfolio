package com.mystock.portfolio.config;

import io.micrometer.common.KeyValue;
import org.springframework.http.client.observation.ClientHttpObservationDocumentation.HighCardinalityKeyNames;
import org.springframework.http.client.observation.ClientHttpObservationDocumentation.LowCardinalityKeyNames;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import org.springframework.http.client.observation.DefaultClientRequestObservationConvention;

/**
 * 바깥 API 호출 지표(http_client_requests_seconds)의 uri 태그를 안전하게 다듬는다.
 *
 * ★ 왜 기본값을 그대로 안 쓰나
 * 스프링은 .uri("...") 에 넘긴 문자열을 그대로 uri 태그로 붙인다. 지금은 전부 {cik} 같은 템플릿이라 값이
 * 들어가지 않지만, 누군가 "?crtfc_key=" + 키 처럼 문자열을 이어 붙이면 **API 키가 지표에 그대로 실려**
 * Prometheus·Grafana 에 남는다(DART 키는 쿼리에, 텔레그램 봇 토큰은 경로에 들어가는 API 다).
 * 종목 코드를 이어 붙이면 종목마다 시계열이 생겨 Prometheus 가 부푼다.
 *
 * 그래서 규칙을 하나 둔다.
 * - 쿼리 문자열(? 뒤)은 항상 버린다
 * - 스킴·호스트는 버린다. 호스트는 client.name 태그에 따로 있다
 * - 경로 조각 하나라도 키처럼 보이면(30자 초과, 또는 영문·숫자·. _ - {} 밖의 글자) 통째로 "none"
 *   텔레그램 봇 토큰은 "숫자:긴문자열" 이라 ':' 에서, API 키는 길이에서 걸린다.
 *   전체 길이로 자르면 "/bot" + 토큰 + "/sendMessage"(약 60자)가 빠져나간다
 */
public class SafeClientRequestObservationConvention extends DefaultClientRequestObservationConvention {

    /** 경로 조각 하나의 최대 길이. API 키·토큰은 대개 32자 이상이다 */
    static final int MAX_SEGMENT = 30;
    private static final java.util.regex.Pattern SEGMENT = java.util.regex.Pattern.compile("[A-Za-z0-9._{}-]*");

    @Override
    protected KeyValue uri(ClientRequestObservationContext context) {
        return KeyValue.of(LowCardinalityKeyNames.URI, safeUri(context.getUriTemplate()));
    }

    /**
     * 전체 주소(http.url). 지표에는 안 실리지만(고카디널리티 값은 트레이싱용) 나중에 트레이싱을 붙이면
     * DART 키가 든 쿼리가 그대로 남는다. 미리 쿼리를 버린다
     */
    @Override
    protected KeyValue requestUri(ClientRequestObservationContext context) {
        if (context.getCarrier() == null) {
            return KeyValue.of(HighCardinalityKeyNames.HTTP_URL, "none");
        }
        java.net.URI u = context.getCarrier().getURI();
        return KeyValue.of(HighCardinalityKeyNames.HTTP_URL, u.getScheme() + "://" + u.getRawAuthority() + u.getRawPath());
    }

    static String safeUri(String template) {
        if (template == null || template.isBlank()) {
            return "none";
        }
        String t = template;
        int query = t.indexOf('?');
        if (query >= 0) {
            t = t.substring(0, query);
        }
        int scheme = t.indexOf("://");
        if (scheme >= 0) {
            int path = t.indexOf('/', scheme + 3);
            t = path >= 0 ? t.substring(path) : "/";
        }
        if (t.isEmpty()) {
            t = "/";
        }
        for (String segment : t.split("/")) {
            if (segment.length() > MAX_SEGMENT || !SEGMENT.matcher(segment).matches()) {
                return "none";
            }
        }
        return t;
    }
}
