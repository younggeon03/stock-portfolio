package com.mystock.portfolio.external.auth;

import com.mystock.portfolio.domain.OAuthToken;
import com.mystock.portfolio.domain.OAuthTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * 토큰을 MySQL 에 보관하는 구현체.
 *
 * 앱을 재시작해도 살아있는 토큰을 그대로 이어 쓰게 해준다.
 *
 * ★ 저장에 실패해도 앱이 멈추면 안 된다
 * DB 가 잠깐 이상해도 토큰 발급 자체는 성공한 상태다.
 * 그걸 못 저장했다고 기업분석이나 잔고 조회까지 막으면 손해다.
 * 그래서 저장 실패는 경고만 남기고 넘어간다. 다음 재시작 때 한 번 더 발급받으면 그만이다.
 */
@Component
public class JpaTokenStore implements TokenStore {

    private static final Logger log = LoggerFactory.getLogger(JpaTokenStore.class);

    private final OAuthTokenRepository repository;
    private final TokenCipher cipher;

    public JpaTokenStore(OAuthTokenRepository repository, TokenCipher cipher) {
        this.repository = repository;
        this.cipher = cipher;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredToken> find(String provider, String ownerKey) {
        try {
            return repository.findByProviderAndOwnerKey(provider, ownerKey)
                    .flatMap(entity -> cipher.decrypt(entity.getAccessToken(), provider, ownerKey)
                            .map(token -> new StoredToken(token, entity.getExpiresAt())));
        } catch (Exception e) {
            log.warn("{} 저장된 토큰을 읽지 못했습니다(새로 발급받습니다): {}", provider, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public void save(String provider, String ownerKey, String accessToken, Instant expiresAt) {
        try {
            String sealed = cipher.encrypt(accessToken, provider, ownerKey);
            repository.findByProviderAndOwnerKey(provider, ownerKey)
                    .ifPresentOrElse(
                            entity -> entity.replace(sealed, expiresAt),
                            () -> repository.save(new OAuthToken(provider, ownerKey, sealed, expiresAt)));
        } catch (Exception e) {
            log.warn("{} 토큰을 저장하지 못했습니다(동작에는 지장 없음): {}", provider, e.getMessage());
        }
    }

    @Override
    @Transactional
    public void delete(String provider, String ownerKey) {
        try {
            repository.deleteByProviderAndOwnerKey(provider, ownerKey);
        } catch (Exception e) {
            log.warn("{} 토큰을 지우지 못했습니다: {}", provider, e.getMessage());
        }
    }
}
