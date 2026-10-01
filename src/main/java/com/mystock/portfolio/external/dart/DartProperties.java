package com.mystock.portfolio.external.dart;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * DART(금융감독원 전자공시) OpenAPI 접속 설정.
 *
 * 토스·나무와 달리 인증키 하나뿐이고 토큰 발급 절차가 없다. 요청마다 crtfc_key 로 붙인다.
 * 키가 비어 있어도 앱은 뜬다. 재무를 못 붙일 뿐 분석은 예전처럼 돈다.
 */
@ConfigurationProperties(prefix = "dart")
public record DartProperties(

        /** 항상 https://opendart.fss.or.kr */
        String baseUrl,

        /** opendart.fss.or.kr 에서 발급받은 40자리 인증키 */
        String apiKey
) {

    public boolean hasKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
