package com.mystock.portfolio.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 액세스 토큰 저장소 */
public interface OAuthTokenRepository extends JpaRepository<OAuthToken, Long> {

    Optional<OAuthToken> findByProviderAndOwnerKey(String provider, String ownerKey);

    void deleteByProviderAndOwnerKey(String provider, String ownerKey);
}
