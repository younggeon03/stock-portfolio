package com.mystock.portfolio.external.toss;

import com.mystock.portfolio.common.AppException;
import com.mystock.portfolio.external.auth.TokenStore;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

/**
 * 토큰 발급 모듈 테스트.
 *
 * 실제 토스 서버를 부르지 않는다. MockRestServiceServer 가 "가짜 토스 서버" 역할을 해서
 * "우리가 보낸 요청이 스펙대로 생겼는지" 를 검사하고, 정해둔 가짜 응답을 돌려준다.
 * 덕분에 진짜 client_id/secret 없이도 코드가 맞게 짜였는지 확인할 수 있다.
 */
class TossAuthServiceTest {

    private static final String BASE_URL = "https://openapi.tossinvest.com";

    /** 테스트용 가짜 토큰 응답 (실제 값 아님) */
    private static final String TOKEN_JSON = """
            {
              "access_token": "test-access-token-abcdefghijklmnop",
              "token_type": "Bearer",
              "expires_in": 86400
            }
            """;

    @Test
    void 토큰_발급_요청은_폼형식으로_스펙대로_보낸다() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        // 가짜 토스 서버가 이런 요청이 올 것이라고 기대한다
        server.expect(requestTo(BASE_URL + "/oauth2/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("grant_type=client_credentials")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("client_id=my-id")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("client_secret=my-secret")))
                .andRespond(withSuccess(TOKEN_JSON, MediaType.APPLICATION_JSON));

        TossAuthService service = new TossAuthService(builder.build(), properties("my-id", "my-secret"), ipLookup(), memoryStore());

        String token = service.getAccessToken();

        assertThat(token).isEqualTo("test-access-token-abcdefghijklmnop");
        assertThat(service.currentTokenExpiresAt()).isNotNull();
        server.verify();
    }

    @Test
    void 한번_발급한_토큰은_다시_발급받지_않고_재사용한다() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        // expect 를 한 번만 걸어두었으므로, 두 번째로 서버를 부르면 테스트가 실패한다.
        // = "재사용이 안 되면 잡아낸다"
        server.expect(requestTo(BASE_URL + "/oauth2/token"))
                .andRespond(withSuccess(TOKEN_JSON, MediaType.APPLICATION_JSON));

        TossAuthService service = new TossAuthService(builder.build(), properties("my-id", "my-secret"), ipLookup(), memoryStore());

        String first = service.getAccessToken();
        String second = service.getAccessToken();

        assertThat(second).isEqualTo(first);
        server.verify();
    }

    @Test
    void 앱을_재시작해도_저장된_토큰을_다시_발급받지_않는다() {
        // 이 기능이 필요한 이유:
        // 토큰 수명이 24시간인데 앱을 재시작할 때마다 새로 받으면 낭비이고,
        // 재발급하는 순간 이전 토큰이 죽어서 다른 곳에서 쓰던 토큰까지 무효가 된다.
        MemoryTokenStore sharedStore = new MemoryTokenStore();

        // 1) 앱이 처음 떴을 때: 발급이 일어난다
        RestClient.Builder firstBuilder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer firstServer = MockRestServiceServer.bindTo(firstBuilder).build();
        firstServer.expect(requestTo(BASE_URL + "/oauth2/token"))
                .andRespond(withSuccess(TOKEN_JSON, MediaType.APPLICATION_JSON));

        TossAuthService before = new TossAuthService(
                firstBuilder.build(), properties("my-id", "my-secret"), ipLookup(), sharedStore);
        String firstToken = before.getAccessToken();
        firstServer.verify();
        assertThat(sharedStore.size()).isEqualTo(1);

        // 2) 앱을 재시작한 상황: 새 인스턴스지만 저장소는 그대로다.
        //    여기서는 서버 호출을 하나도 기대하지 않는다. 호출하면 테스트가 실패한다.
        RestClient.Builder secondBuilder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer secondServer = MockRestServiceServer.bindTo(secondBuilder).build();

        TossAuthService after = new TossAuthService(
                secondBuilder.build(), properties("my-id", "my-secret"), ipLookup(), sharedStore);

        assertThat(after.getAccessToken()).isEqualTo(firstToken);
        secondServer.verify();   // 호출이 한 건도 없어야 통과한다
    }

    @Test
    void 토큰을_버리면_저장소에서도_지워진다() {
        // 서버가 401 을 줬다는 건 그 토큰이 죽었다는 뜻이다.
        // DB 에 남겨두면 재시작 후 죽은 토큰을 다시 꺼내 쓰게 된다.
        MemoryTokenStore store = new MemoryTokenStore();

        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL + "/oauth2/token"))
                .andRespond(withSuccess(TOKEN_JSON, MediaType.APPLICATION_JSON));

        TossAuthService service = new TossAuthService(
                builder.build(), properties("my-id", "my-secret"), ipLookup(), store);

        service.getAccessToken();
        assertThat(store.size()).isEqualTo(1);

        service.invalidate();
        assertThat(store.size()).isZero();
    }

    @Test
    void 키가_다르면_토큰이_서로_섞이지_않는다() {
        // 나중에 사람마다 자기 API 키를 등록하는 구조가 되면 이 성질이 필요하다.
        assertThat(TokenStore.ownerKeyOf("client-A"))
                .isNotEqualTo(TokenStore.ownerKeyOf("client-B"));

        // 같은 키는 항상 같은 값이어야 재시작 후에도 찾을 수 있다
        assertThat(TokenStore.ownerKeyOf("client-A"))
                .isEqualTo(TokenStore.ownerKeyOf("client-A"));

        // client_id 가 그대로 저장되지 않는다
        assertThat(TokenStore.ownerKeyOf("client-A")).doesNotContain("client-A");
    }

    @Test
    void 키가_비어있으면_무엇을_채워야_하는지_알려준다() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer.bindTo(builder).build();

        TossAuthService service = new TossAuthService(builder.build(), properties("", ""), ipLookup(), memoryStore());

        assertThatThrownBy(service::getAccessToken)
                .isInstanceOf(AppException.class)
                .hasMessageContaining("TOSS_CLIENT_ID")
                .hasMessageContaining(".env");
    }

    @Test
    void 인증실패_401은_키를_확인하라고_알려준다() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo(BASE_URL + "/oauth2/token"))
                .andRespond(withUnauthorizedRequest());

        TossAuthService service = new TossAuthService(builder.build(), properties("wrong", "wrong"), ipLookup(), memoryStore());

        assertThatThrownBy(service::getAccessToken)
                .isInstanceOf(AppException.class)
                .hasMessageContaining("401")
                .hasMessageContaining("client_id");
    }

    /** accountSeq 는 인증과 무관하므로 비워둔다(= 자동 선택) */
    private TossApiProperties properties(String clientId, String clientSecret) {
        return new TossApiProperties(BASE_URL, clientId, clientSecret, "");
    }

    /**
     * IP 조회기는 403 안내문을 만들 때만 쓰인다.
     * 아래 테스트들은 403 을 만들지 않으므로 실제로 호출되지 않는다(= 네트워크를 타지 않는다).
     */
    private PublicIpLookup ipLookup() {
        return new PublicIpLookup(RestClient.create());
    }

    /** DB 없이 테스트하려고 메모리에만 담아두는 저장소 */
    private TokenStore memoryStore() {
        return new MemoryTokenStore();
    }

    /** 진짜 DB 대신 Map 하나로 동작하는 저장소 */
    private static class MemoryTokenStore implements TokenStore {

        private final Map<String, StoredToken> saved = new HashMap<>();

        @Override
        public Optional<StoredToken> find(String provider, String ownerKey) {
            return Optional.ofNullable(saved.get(provider + ":" + ownerKey));
        }

        @Override
        public void save(String provider, String ownerKey, String accessToken, Instant expiresAt) {
            saved.put(provider + ":" + ownerKey, new StoredToken(accessToken, expiresAt));
        }

        @Override
        public void delete(String provider, String ownerKey) {
            saved.remove(provider + ":" + ownerKey);
        }

        int size() {
            return saved.size();
        }
    }
}
