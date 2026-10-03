package com.mystock.portfolio.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 바깥 API 호출 지표의 uri 태그. 키·토큰·종목 코드가 지표에 실리면 안 된다.
 * 템플릿({cik})은 값이 안 들어가므로 그대로 두고, 쿼리·호스트는 버리고, 이어 붙인 듯 긴 것은 none.
 */
class SafeClientRequestObservationConventionTest {

    @Test
    void 템플릿은_경로만_남긴다() {
        assertThat(SafeClientRequestObservationConvention.safeUri("https://data.sec.gov/submissions/CIK{cik}.json"))
                .isEqualTo("/submissions/CIK{cik}.json");
        assertThat(SafeClientRequestObservationConvention.safeUri("/bot{token}/sendMessage")).isEqualTo("/bot{token}/sendMessage");
        assertThat(SafeClientRequestObservationConvention.safeUri("/oauth2/token")).isEqualTo("/oauth2/token");
    }

    /** 누군가 "?crtfc_key=" + 키 처럼 이어 붙여도 키는 지표에 안 남는다 */
    @Test
    void 쿼리_문자열은_항상_버린다() {
        assertThat(SafeClientRequestObservationConvention.safeUri("/api/list.json?crtfc_key=abc123secret&corp_code=00126380"))
                .isEqualTo("/api/list.json")
                .doesNotContain("abc123secret");
    }

    @Test
    void 호스트만_있으면_루트() {
        assertThat(SafeClientRequestObservationConvention.safeUri("https://api.openfigi.com")).isEqualTo("/");
    }

    @Test
    void 템플릿이_없으면_none() {
        assertThat(SafeClientRequestObservationConvention.safeUri(null)).isEqualTo("none");
        assertThat(SafeClientRequestObservationConvention.safeUri("")).isEqualTo("none");
    }

    /** 템플릿 대신 토큰을 이어 붙인 경우. 데모 값이다 */
    @Test
    void 토큰을_이어_붙인_경로는_none() {
        String concatenated = "/bot" + "1234567890:AAHdqTcvCH1vGWJxfSeofSAs0K5PALDsaw" + "/sendMessage";
        assertThat(SafeClientRequestObservationConvention.safeUri(concatenated)).isEqualTo("none");
    }

    @Test
    void 키처럼_긴_조각이_있으면_none() {
        assertThat(SafeClientRequestObservationConvention.safeUri("/v1/0123456789abcdef0123456789abcdef01234567/data"))
                .isEqualTo("none");
    }
}
