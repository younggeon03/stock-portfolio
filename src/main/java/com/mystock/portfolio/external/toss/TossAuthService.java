package com.mystock.portfolio.external.toss;

import com.mystock.portfolio.common.AppException;
import com.mystock.portfolio.external.auth.TokenStore;
import com.mystock.portfolio.external.toss.dto.TossTokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.Instant;

/**
 * 토스증권 OAuth2 액세스 토큰 발급/보관 담당.
 *
 * === 왜 캐싱이 필수인가 ===
 * 토스 문서상 "client 당 유효한 access token 은 1개"이고, 재발급하면 이전 토큰이 즉시 무효화된다.
 * 즉 API 호출마다 토큰을 새로 받으면 직전 토큰이 죽어버려서, 동시에 진행 중인 다른 요청이 401 로 실패한다.
 * 그래서 한 번 받은 토큰을 만료 직전까지 재사용한다.
 *
 * === 동시성 ===
 * 웹 요청은 여러 스레드에서 동시에 들어온다. 두 스레드가 동시에 "토큰 없네?" 하고 각자 발급하면
 * 위의 이유로 한쪽이 무효화된다. 그래서 synchronized + 이중 검사(double-checked)로 발급은 한 번만 일어나게 한다.
 */
@Service
public class TossAuthService {

    private static final Logger log = LoggerFactory.getLogger(TossAuthService.class);

    /**
     * 만료 시각 직전에 쓰면 "쓰는 도중 만료" 가 날 수 있으므로 1분 여유를 두고 미리 재발급한다.
     */
    private static final Duration EXPIRY_MARGIN = Duration.ofMinutes(1);

    /** 토큰 저장소에서 이 증권사를 구분하는 이름 */
    private static final String PROVIDER = "TOSS";

    private final RestClient tossRestClient;
    private final TossApiProperties properties;
    private final PublicIpLookup publicIpLookup;
    private final TokenStore tokenStore;

    /**
     * 현재 보관 중인 토큰. 다른 스레드가 바꾼 값이 즉시 보이도록 volatile.
     * DB 를 매번 읽지 않으려는 1차 캐시다.
     */
    private volatile CachedToken cachedToken;

    public TossAuthService(RestClient tossRestClient, TossApiProperties properties,
                           PublicIpLookup publicIpLookup, TokenStore tokenStore) {
        this.tossRestClient = tossRestClient;
        this.properties = properties;
        this.publicIpLookup = publicIpLookup;
        this.tokenStore = tokenStore;
    }

    /**
     * 토스 API 호출에 쓸 액세스 토큰을 돌려준다.
     * 보관 중인 토큰이 아직 쓸 만하면 그대로 재사용하고, 없거나 곧 만료면 새로 발급한다.
     */
    public String getAccessToken() {
        CachedToken token = this.cachedToken;
        if (isUsable(token)) {
            return token.accessToken();
        }

        // 여기 들어온 스레드가 여럿일 수 있으므로 한 줄로 세운다.
        synchronized (this) {
            // 줄 서 있는 동안 앞 스레드가 이미 발급했을 수 있으니 다시 확인 (이중 검사)
            CachedToken latest = this.cachedToken;
            if (isUsable(latest)) {
                return latest.accessToken();
            }

            // 메모리에 없으면 DB 를 본다. 앱을 재시작한 직후가 이 경우다.
            // 토큰 수명이 24시간이라 재시작할 때마다 새로 받으면 낭비가 크다.
            CachedToken stored = loadFromStore();
            if (isUsable(stored)) {
                this.cachedToken = stored;
                log.info("저장해둔 토스증권 토큰을 재사용합니다. 만료 예정 = {}", stored.expiresAt());
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

    /** 이 토큰이 누구 것인지 구분하는 열쇠 (client_id 해시) */
    private String ownerKey() {
        return TokenStore.ownerKeyOf(properties.clientId());
    }

    /**
     * 보관 중인 토큰의 만료 시각. 아직 한 번도 발급하지 않았으면 null.
     * (동작 확인용 화면에서 "언제까지 유효한지" 보여주려고 열어둔 메서드)
     */
    public Instant currentTokenExpiresAt() {
        CachedToken token = this.cachedToken;
        return token == null ? null : token.expiresAt();
    }

    /**
     * 토큰을 강제로 버린다. 다음 getAccessToken() 호출 때 새로 발급된다.
     * 서버가 401 을 돌려준 경우(토큰이 서버 쪽에서 무효화된 경우) 호출해서 한 번 더 시도할 때 쓴다.
     */
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

        // OAuth2 표준대로 JSON 이 아니라 application/x-www-form-urlencoded 로 보낸다.
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());

        TossTokenResponse response;
        try {
            response = tossRestClient.post()
                    .uri("/oauth2/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TossTokenResponse.class);
        } catch (RestClientResponseException e) {
            // 토스가 4xx/5xx 를 돌려준 경우: 상태코드별로 원인을 짚어준다.
            throw new AppException(describeTokenError(e), e);
        } catch (Exception e) {
            // 네트워크 단절, DNS 실패, 타임아웃 등
            throw new AppException("토스증권 토큰 발급 요청 자체가 실패했습니다(네트워크 확인): " + e.getMessage(), e);
        }

        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
            throw new AppException("토스증권이 access_token 을 내려주지 않았습니다. 응답 형식이 바뀌었는지 확인하세요.");
        }

        Instant expiresAt = Instant.now().plusSeconds(response.expiresIn());
        // 토큰 값 자체는 절대 로그에 남기지 않는다. 만료 시각만 남긴다.
        log.info("토스증권 access token 발급 완료. 만료 예정 시각 = {}", expiresAt);
        return new CachedToken(response.accessToken(), expiresAt);
    }

    /**
     * .env 에 값이 안 채워진 채로 호출되면, 401 같은 모호한 에러 대신 무엇을 채워야 하는지 알려준다.
     */
    private void requireCredentials() {
        if (isBlank(properties.clientId()) || isBlank(properties.clientSecret())) {
            throw new AppException("""
                    토스증권 API 키가 설정되지 않았습니다.
                    프로젝트 루트의 .env 파일에 아래 두 줄을 채워 넣고 앱을 재시작하세요.
                      TOSS_CLIENT_ID=발급받은_클라이언트_ID
                      TOSS_CLIENT_SECRET=발급받은_시크릿
                    (토스증권 WTS > 설정 > Open API 에서 발급)""");
        }
        if (isBlank(properties.baseUrl())) {
            throw new AppException("toss.base-url 이 비어 있습니다. application.yml 을 확인하세요.");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 토큰 발급 실패 응답을 사람이 읽을 수 있는 안내로 바꾼다.
     * 상태코드별 의미는 토스 OpenAPI 스펙(POST /oauth2/token) 기준.
     */
    private String describeTokenError(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        String base = "토스증권 토큰 발급 실패 (HTTP " + status + "). ";
        return base + switch (status) {
            case 400 -> "필수 파라미터가 누락되었거나 grant_type 이 잘못되었습니다.";
            case 401 -> "client_id 또는 client_secret 이 틀렸거나, 클라이언트가 비활성 상태입니다. .env 값을 다시 확인하세요.";
            // 403 은 "IP 가 바뀌었을 때" 가장 흔하다. 그래서 지금 IP 가 뭔지까지 같이 알려준다.
            case 403 -> "허용되지 않은 IP 에서의 요청입니다.\n"
                    + "토스증권 WTS > 설정 > Open API > 허용 IP 관리 에서 등록하세요.\n"
                    + publicIpLookup.registerGuide();
            case 429 -> "요청 한도(AUTH 그룹)를 초과했습니다. 잠시 후 다시 시도하세요.";
            default -> "응답 본문: " + e.getResponseBodyAsString();
        };
    }

    /**
     * 발급받은 토큰과 그 만료 시각을 함께 들고 다니는 값 객체.
     */
    private record CachedToken(String accessToken, Instant expiresAt) {
    }
}
