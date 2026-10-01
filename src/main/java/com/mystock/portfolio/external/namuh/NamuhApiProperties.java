package com.mystock.portfolio.external.namuh;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 나무증권(NAMUH PLUG) Open API 접속 설정.
 *
 * ★ 주의: 나무증권 API 는 두 종류가 있다.
 *   - 구형 (mynamuh.com): 윈도우 32bit C++ DLL 방식. 2023-05-31 신규 신청 종료. 서버에서 못 쓴다.
 *   - 신형 (NAMUH PLUG, nhplug.com): REST 방식. 우리가 쓰는 건 이것.
 *
 * 토스와 설정 모양이 거의 같다. 다른 점은 파라미터 이름(appkey/appsecretkey)과
 * 포트 번호가 붙은 주소(:8443) 정도다.
 */
@ConfigurationProperties(prefix = "namuh")
public record NamuhApiProperties(

        /** API 서버 주소. 포트 8443 이 반드시 붙는다 */
        String baseUrl,

        /** NAMUH PLUG 에서 발급받은 appkey */
        String appKey,

        /** NAMUH PLUG 에서 발급받은 appsecretkey. .env 에만 둔다 */
        String appSecret,

        /**
         * 사용할 계좌번호.
         * 비워두면 계좌 목록을 조회해서 첫 번째 계좌를 자동으로 쓴다.
         *
         * 토스는 숫자 accountSeq 를 헤더에 넣었지만, 나무는 계좌번호 문자열을
         * 요청 본문(act_no)에 넣는다. 그래서 String 이다.
         */
        String accountNo
) {

    /** 키가 채워져 있는지 */
    public boolean hasCredentials() {
        return appKey != null && !appKey.isBlank()
                && appSecret != null && !appSecret.isBlank();
    }

    /** .env 의 NAMUH_ACCOUNT_NO. 비어 있으면 null (= 자동 선택) */
    public String accountNoOrNull() {
        return (accountNo == null || accountNo.isBlank()) ? null : accountNo.trim();
    }
}
