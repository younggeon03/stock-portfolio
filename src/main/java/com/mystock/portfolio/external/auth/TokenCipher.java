package com.mystock.portfolio.external.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * 증권사 토큰을 DB 에 넣기 전에 암호화한다.
 *
 * ★ 왜 필요한가
 * 토큰 하나면 24시간 동안 계좌를 조회할 수 있다. 내 PC 의 DB 에서는 .env 와 위험이 비슷했지만
 * 서버에 올리면 DB 백업 파일, 덤프, 다른 컨테이너 등 토큰이 새어 나갈 길이 늘어난다.
 * DB 에는 암호문만 두고, 푸는 키는 환경변수(TOKEN_ENCRYPTION_KEY)에만 둔다. 둘이 같이 새야 뚫린다.
 *
 * ★ 방식: AES-256-GCM
 * GCM 은 암호화와 위조 검사를 같이 한다. 누가 암호문을 한 글자만 바꿔도 풀 때 실패한다.
 * 매번 새 IV(12바이트)를 쓰므로 같은 토큰도 저장할 때마다 다른 암호문이 된다.
 * provider·ownerKey 를 추가 인증 데이터(AAD)로 묶어서, 다른 행의 암호문을 옮겨 붙이면 풀리지 않는다.
 *
 * ★ 저장 형식: "enc:v1:" + base64(IV || 암호문+태그)
 * 접두사로 평문과 암호문을 구분한다. 키를 처음 넣은 날 DB 에 있던 평문 토큰은 그대로 읽고,
 * 다음 저장 때 암호문으로 바뀐다. 토큰 수명이 24시간이라 하루면 전부 바뀐다.
 * 방식을 바꾸게 되면 v2 를 만들어 v1 도 계속 읽게 한다.
 *
 * ★ 키가 없으면 평문으로 둔다
 * 처음 받아서 돌려보는 사람에게 키 생성부터 시키면 진입이 막힌다. 대신 기동할 때 경고를 남긴다.
 * 서버에서는 반드시 넣는다 (docs/운영.md).
 */
@Component
public class TokenCipher {

    private static final Logger log = LoggerFactory.getLogger(TokenCipher.class);

    static final String PREFIX = "enc:v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public TokenCipher(@Value("${security.token-encryption-key:}") String base64Key) {
        this.key = parseKey(base64Key);
        if (key == null) {
            log.warn("TOKEN_ENCRYPTION_KEY 가 없어 증권사 토큰을 평문으로 저장합니다. 서버에서는 반드시 넣으세요 (docs/운영.md)");
        }
    }

    /** 키가 들어 있는지. 화면이나 로그에 상태를 알릴 때 쓴다 */
    public boolean enabled() {
        return key != null;
    }

    /** 저장할 값. 키가 없으면 평문 그대로 */
    public String encrypt(String plain, String provider, String ownerKey) {
        if (key == null) {
            return plain;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad(provider, ownerKey));
            byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = ByteBuffer.allocate(iv.length + sealed.length).put(iv).put(sealed).array();
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            // JDK 기본 알고리즘이라 여기 올 일은 없다. 오면 평문으로 저장하지 않고 저장 자체를 포기시킨다
            throw new IllegalStateException("토큰 암호화에 실패했습니다", e);
        }
    }

    /**
     * 저장된 값을 토큰으로 되돌린다. 못 풀면 비어 있다.
     *
     * 비어 있으면 부르는 쪽이 토큰을 새로 발급받는다. 키를 바꿨거나 잃어버렸을 때
     * 앱이 멈추지 않고 하루치 토큰만 버리고 넘어가게 하려는 것이다.
     */
    public Optional<String> decrypt(String stored, String provider, String ownerKey) {
        if (stored == null || stored.isBlank()) {
            return Optional.empty();
        }
        if (!stored.startsWith(PREFIX)) {
            // 키를 넣기 전에 저장된 평문. 다음 저장 때 암호문으로 바뀐다
            return Optional.of(stored);
        }
        if (key == null) {
            log.warn("{} 토큰이 암호화돼 있는데 TOKEN_ENCRYPTION_KEY 가 없습니다. 새로 발급받습니다", provider);
            return Optional.empty();
        }
        try {
            byte[] in = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, in, 0, IV_BYTES));
            cipher.updateAAD(aad(provider, ownerKey));
            byte[] plain = cipher.doFinal(in, IV_BYTES, in.length - IV_BYTES);
            return Optional.of(new String(plain, StandardCharsets.UTF_8));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            log.warn("{} 저장된 토큰을 풀지 못했습니다(키가 바뀌었거나 값이 변조됨). 새로 발급받습니다", provider);
            return Optional.empty();
        }
    }

    private static byte[] aad(String provider, String ownerKey) {
        return (provider + ":" + ownerKey).getBytes(StandardCharsets.UTF_8);
    }

    /** base64 로 된 32바이트 키. 비어 있으면 null, 모양이 틀리면 기동을 막는다 */
    static SecretKeySpec parseKey(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            return null;
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("TOKEN_ENCRYPTION_KEY 가 base64 가 아닙니다. 만드는 법은 .env.example 7번", e);
        }
        if (raw.length != 32) {
            // 틀린 키로 조용히 돌면 나중에 아무것도 못 푼다. 기동할 때 바로 알린다
            throw new IllegalStateException("TOKEN_ENCRYPTION_KEY 는 32바이트(base64 44자)여야 합니다. 지금 " + raw.length + "바이트");
        }
        return new SecretKeySpec(raw, "AES");
    }
}
