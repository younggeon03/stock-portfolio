package com.mystock.portfolio.external.namuh;

import com.mystock.portfolio.common.AppException;
import com.mystock.portfolio.external.auth.TokenStore;
import com.mystock.portfolio.external.namuh.dto.NamuhTokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/**
 * 나무증권 액세스 토큰 발급/보관 담당.
 *
 * 구조는 TossAuthService 와 판박이다. 나무증권도 똑같이
 *   - OAuth2 client_credentials 방식
 *   - application/x-www-form-urlencoded 로 전송
 *   - 24시간 유효
 *   - "만료 전 재발급하지 말 것" 이라는 주의사항
 * 을 갖고 있다.
 *
 * 다른 점은 두 가지뿐이다.
 *   1. 파라미터 이름이 client_id/client_secret 이 아니라 appkey/appsecretkey
 *   2. scope=oob 를 같이 보내야 한다
 *
 * ★ 토큰 발급 자체에 "초당 1회" 제한이 걸려 있다.
 * 캐싱이 없으면 화면 새로고침 몇 번에 바로 막힌다.
 */
@Service
public class NamuhAuthService {

    private static final Logger log = LoggerFactory.getLogger(NamuhAuthService.class);

    /** 만료 직전에 쓰다가 중간에 만료되는 걸 막으려고 1분 여유를 둔다 */
    private static final Duration EXPIRY_MARGIN = Duration.ofMinutes(1);

    /** 토큰 저장소에서 이 증권사를 구분하는 이름 */
    private static final String PROVIDER = "NAMUH";

    private final RestClient namuhRestClient;
    private final NamuhApiProperties properties;
    private final TokenStore tokenStore;

    /** DB 를 매번 읽지 않으려는 1차 캐시 */
    private volatile CachedToken cachedToken;

    public NamuhAuthService(RestClient namuhRestClient, NamuhApiProperties properties, TokenStore tokenStore) {
        this.namuhRestClient = namuhRestClient;
        this.properties = properties;
        this.tokenStore = tokenStore;
    }

    /** 나무증권 API 호출에 쓸 액세스 토큰. 쓸 만한 게 있으면 재사용한다. */
    public String getAccessToken() {
        CachedToken token = this.cachedToken;
        if (isUsable(token)) {
            return token.accessToken();
        }

        synchronized (this) {
            CachedToken latest = this.cachedToken;
            if (isUsable(latest)) {
                return latest.accessToken();
            }

            // 메모리에 없으면 DB 를 본다. 앱을 재시작한 직후가 이 경우다.
            // 나무는 토큰 발급 자체가 초당 1회 제한이라 아껴 쓰는 게 특히 중요하다.
            CachedToken stored = loadFromStore();
            if (isUsable(stored)) {
                this.cachedToken = stored;
                log.info("저장해둔 나무증권 토큰을 재사용합니다. 만료 예정 = {}", stored.expiresAt());
                return stored.accessToken();
            }

            CachedToken issued = issueNewToken();
            this.cachedToken = issued;
            tokenStore.save(PROVIDER, ownerKey(), issued.accessToken(), issued.expiresAt());
            return issued.accessToken();
        }
    }

    /** DB 에 저장된 토큰을 읽어온다 */
    private CachedToken loadFromStore() {
        return tokenStore.find(PROVIDER, ownerKey())
                .map(stored -> new CachedToken(stored.accessToken(), stored.expiresAt()))
                .orElse(null);
    }

    /** 이 토큰이 누구 것인지 구분하는 열쇠 (appkey 해시) */
    private String ownerKey() {
        return TokenStore.ownerKeyOf(properties.appKey());
    }

    /** 보관 중인 토큰의 만료 시각. 아직 발급 전이면 null */
    public Instant currentTokenExpiresAt() {
        CachedToken token = this.cachedToken;
        return token == null ? null : token.expiresAt();
    }

    /** 토큰을 버린다. 401 을 받았을 때 한 번 더 시도하려고 쓴다. */
    public void invalidate() {
        this.cachedToken = null;
        // DB 에 남겨두면 재시작 후 죽은 토큰을 다시 꺼내 쓰게 되므로 같이 지운다
        tokenStore.delete(PROVIDER, ownerKey());
    }

    private boolean isUsable(CachedToken token) {
        return token != null && Instant.now().isBefore(token.expiresAt().minus(EXPIRY_MARGIN));
    }

    private CachedToken issueNewToken() {
        requireCredentials();

        // ★★ 여기가 토스와 결정적으로 다른 부분이다 ★★
        //
        // 원래는 토스처럼 MultiValueMap 을 .body(form) 으로 넘기면 끝이다.
        // 그런데 나무증권 게이트웨이는 Content-Type 이 정확히
        //   application/x-www-form-urlencoded
        // 여야 하고, 뒤에 charset 이 붙으면
        //   application/x-www-form-urlencoded;charset=UTF-8
        // 이것을 거부하고 403 을 돌려준다.
        //
        // 문제는 스프링의 폼 변환기(FormHttpMessageConverter)가 charset 을 "자동으로 붙인다" 는 것이다.
        // 끄는 설정이 없다.
        //
        // 그래서 폼 문자열을 직접 만들고 byte[] 로 보낸다.
        // byte[] 로 보내면 스프링이 Content-Type 에 손을 대지 않아서 우리가 지정한 값이 그대로 나간다.
        byte[] body = toFormBody(
                "appkey", properties.appKey(),
                "appsecretkey", properties.appSecret(),
                "grant_type", "client_credentials",
                "scope", "oob");          // 나무증권은 scope 값을 요구한다

        NamuhTokenResponse response;
        try {
            response = namuhRestClient.post()
                    .uri("/oauth2/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(NamuhTokenResponse.class);
        } catch (RestClientResponseException e) {
            throw new AppException(describeTokenError(e), e);
        } catch (Exception e) {
            throw new AppException("나무증권 토큰 발급 요청이 실패했습니다(네트워크/주소 확인): " + e.getMessage(), e);
        }

        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
            throw new AppException("나무증권이 access_token 을 내려주지 않았습니다. 응답 형식을 확인하세요.");
        }

        Instant expiresAt = Instant.now().plusSeconds(response.expiresIn());
        log.info("나무증권 access token 발급 완료. 만료 예정 시각 = {}", expiresAt);
        return new CachedToken(response.accessToken(), expiresAt);
    }

    private void requireCredentials() {
        if (!properties.hasCredentials()) {
            throw new AppException("""
                    나무증권 API 키가 설정되지 않았습니다.
                    프로젝트 루트의 .env 파일에 아래 두 줄을 채워 넣고 앱을 재시작하세요.
                      NAMUH_APP_KEY=발급받은_appkey
                      NAMUH_APP_SECRET=발급받은_appsecretkey
                    (NAMUH PLUG, https://www.nhplug.com 에서 발급)""");
        }
        if (properties.baseUrl() == null || properties.baseUrl().isBlank()) {
            throw new AppException("namuh.base-url 이 비어 있습니다. application.yml 을 확인하세요.");
        }
    }

    private String describeTokenError(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        String head = "나무증권 토큰 발급 실패 (HTTP " + status + "). ";
        return head + switch (status) {
            case 400 -> "요청 형식이 잘못되었습니다. appkey/appsecretkey/grant_type/scope 를 확인하세요.";
            case 401 -> "appkey 또는 appsecretkey 가 틀렸습니다. .env 값을 다시 확인하세요.";
            case 403 -> "접근이 거부되었습니다. NAMUH PLUG 에서 API 사용신청이 완료되었는지 확인하세요.";
            case 429 -> "토큰 발급은 초당 1회로 제한됩니다. 잠시 후 다시 시도하세요.";
            default -> "응답 본문: " + e.getResponseBodyAsString();
        };
    }

    /**
     * "키1=값1&키2=값2" 형태의 폼 본문을 직접 만든다.
     *
     * 값에 &, = 같은 특수문자가 들어가도 깨지지 않도록 URL 인코딩한다.
     * (시크릿에 특수문자가 들어있는 경우가 흔하다)
     *
     * @param keyValues 키, 값, 키, 값... 순서로 넘긴다
     */
    private static byte[] toFormBody(String... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("키와 값을 짝수 개로 넘겨야 합니다.");
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keyValues.length; i += 2) {
            if (i > 0) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(keyValues[i], StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(keyValues[i + 1], StandardCharsets.UTF_8));
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private record CachedToken(String accessToken, Instant expiresAt) {
    }
}
