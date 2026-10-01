package com.mystock.portfolio.external.auth;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

/**
 * 발급받은 액세스 토큰을 보관하는 곳.
 *
 * ★ 왜 필요한가
 * 지금까지는 토큰을 메모리에만 들고 있었다. 그래서 앱을 재시작할 때마다 토큰을 새로 받았다.
 * 그런데 토스도 나무도 "client 당 유효한 토큰은 1개" 이고 재발급하면 이전 토큰이 즉시 죽는다.
 * 게다가 나무는 토큰 발급 자체가 초당 1회로 제한된다.
 * 토큰 수명이 24시간인데 앱을 하루에 열 번 재시작하면 열 번 다 새로 받는 셈이라 낭비가 크다.
 *
 * DB 에 저장해두면 재시작해도 살아있는 토큰을 그대로 이어 쓴다.
 *
 * ★ 인터페이스로 둔 이유
 * 테스트에서 DB 없이 메모리 구현으로 바꿔 끼우기 위해서다.
 */
public interface TokenStore {

    /** 보관된 토큰. 없으면 빈 Optional */
    Optional<StoredToken> find(String provider, String ownerKey);

    /** 토큰을 저장한다. 같은 (provider, ownerKey) 가 있으면 덮어쓴다. */
    void save(String provider, String ownerKey, String accessToken, Instant expiresAt);

    /** 토큰을 버린다. 서버가 401 을 돌려줬을 때 쓴다. */
    void delete(String provider, String ownerKey);

    /** 보관된 토큰 한 건 */
    record StoredToken(String accessToken, Instant expiresAt) {
    }

    /**
     * 토큰을 누구 것으로 묶을지 정하는 열쇠를 만든다.
     *
     * 지금은 앱 주인 한 사람뿐이라 client_id 하나에서 만들어진 값 하나만 쓰인다.
     * 나중에 사람마다 자기 API 키를 등록하는 구조가 되면, 키가 다르니 이 값도 달라져서
     * 사람별로 토큰이 자연스럽게 나뉜다. 그때 이 코드를 고칠 필요가 없다.
     *
     * client_id 를 그대로 저장하지 않고 해시로 바꾸는 이유는, DB 를 들여다봐도
     * 어떤 키를 쓰는지 바로 알 수 없게 하기 위해서다.
     */
    static String ownerKeyOf(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return "unknown";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(clientId.getBytes(StandardCharsets.UTF_8));
            // 앞 16바이트(32글자)면 충돌 걱정 없이 충분하다
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (Exception e) {
            throw new IllegalStateException("토큰 보관 키를 만들지 못했습니다.", e);
        }
    }
}
