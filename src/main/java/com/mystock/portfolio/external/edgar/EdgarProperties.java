package com.mystock.portfolio.external.edgar;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SEC EDGAR 접속 설정.
 *
 * 키가 없다. 대신 SEC 가 모든 요청에 "누가 부르는지" 를 User-Agent 로 밝히라고 요구한다.
 * 형식은 "앱이름 연락처이메일". 이게 없거나 흔한 브라우저 값이면 www.sec.gov 가 403 을 준다.
 * (data.sec.gov 는 느슨하지만 종목 매핑 파일이 www 쪽에 있어서 결국 필요하다.)
 *
 * 비어 있으면 앱은 뜨고, 미국 종목 재무만 예전처럼 웹 조사로 간다.
 */
@ConfigurationProperties(prefix = "sec")
public record EdgarProperties(

        /** 예: "PortfolioApp you@example.com" */
        String userAgent
) {

    public boolean hasUserAgent() {
        return userAgent != null && !userAgent.isBlank();
    }
}
