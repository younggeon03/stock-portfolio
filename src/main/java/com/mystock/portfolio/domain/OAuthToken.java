package com.mystock.portfolio.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * 증권사에서 발급받은 액세스 토큰을 보관한다.
 *
 * 앱을 재시작해도 살아있는 토큰을 이어 쓰려고 DB 에 넣는다.
 * (토스·나무 모두 토큰 수명이 24시간인데, 재발급하면 이전 토큰이 즉시 죽는다)
 *
 * ★ 보안 메모
 * 토큰은 비밀번호에 준하는 값이다. 이 값만 있으면 24시간 동안 계좌를 조회할 수 있다.
 * 지금은 내 PC 의 내 MySQL 에만 저장되므로 .env 에 키를 두는 것과 위험 수준이 비슷하다.
 * 서버에 배포해서 여러 사람이 쓰게 된다면 반드시 암호화해서 저장해야 한다.
 */
@Entity
@Table(name = "oauth_token",
        uniqueConstraints = @UniqueConstraint(name = "uk_oauth_token_provider_owner",
                columnNames = {"provider", "owner_key"}))
public class OAuthToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 어느 증권사 토큰인지. TOSS 또는 NAMUH */
    @Column(nullable = false, length = 20)
    private String provider;

    /**
     * 이 토큰이 누구 것인지 나타내는 열쇠.
     * API 키를 해시한 값이라, 사람마다 키가 다르면 자동으로 나뉜다.
     */
    @Column(name = "owner_key", nullable = false, length = 64)
    private String ownerKey;

    /**
     * 액세스 토큰 값.
     * 토스는 800자가 넘고 나무는 400자 가까이 된다. 길이가 늘어도 잘리지 않도록 TEXT 로 잡는다.
     */
    @Lob
    @Column(name = "access_token", nullable = false, columnDefinition = "TEXT")
    private String accessToken;

    /** 만료 시각 */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** 발급받은 시각 */
    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    protected OAuthToken() {
        // JPA 기본 생성자
    }

    public OAuthToken(String provider, String ownerKey, String accessToken, Instant expiresAt) {
        this.provider = provider;
        this.ownerKey = ownerKey;
        this.accessToken = accessToken;
        this.expiresAt = expiresAt;
        this.issuedAt = LocalDateTime.now();
    }

    /** 새 토큰으로 갈아끼운다 */
    public void replace(String accessToken, Instant expiresAt) {
        this.accessToken = accessToken;
        this.expiresAt = expiresAt;
        this.issuedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getProvider() {
        return provider;
    }

    public String getOwnerKey() {
        return ownerKey;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }
}
