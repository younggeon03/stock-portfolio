package com.mystock.portfolio.external.auth;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 토큰 암호화. DB 에 있는 값만으로는 토큰을 못 쓰게 하는 게 목적이다.
 */
class TokenCipherTest {

    /** 테스트 전용 키. 실제 키가 아니다 */
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String OTHER_KEY = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
    private static final String TOKEN = "eyJhbGciOiJIUzI1NiJ9.demo-token-value";

    private final TokenCipher cipher = new TokenCipher(KEY);

    @Test
    void 암호화한_값은_원래_토큰이_보이지_않고_다시_풀린다() {
        String stored = cipher.encrypt(TOKEN, "TOSS", "owner-a");

        assertThat(stored).startsWith(TokenCipher.PREFIX).doesNotContain(TOKEN);
        assertThat(cipher.decrypt(stored, "TOSS", "owner-a")).contains(TOKEN);
    }

    @Test
    void 같은_토큰도_저장할_때마다_암호문이_다르다() {
        // IV 를 매번 새로 뽑지 않으면 같은 토큰끼리 같은 암호문이 되어 비교로 정보가 샌다
        assertThat(cipher.encrypt(TOKEN, "TOSS", "owner-a")).isNotEqualTo(cipher.encrypt(TOKEN, "TOSS", "owner-a"));
    }

    @Test
    void 다른_행의_암호문을_옮겨_붙이면_풀리지_않는다() {
        String toss = cipher.encrypt(TOKEN, "TOSS", "owner-a");

        assertThat(cipher.decrypt(toss, "NAMUH", "owner-a")).isEmpty();
        assertThat(cipher.decrypt(toss, "TOSS", "owner-b")).isEmpty();
    }

    @Test
    void 한_글자라도_바뀌면_풀리지_않는다() {
        String stored = cipher.encrypt(TOKEN, "TOSS", "owner-a");
        char last = stored.charAt(stored.length() - 2);
        String tampered = stored.substring(0, stored.length() - 2) + (last == 'A' ? 'B' : 'A') + stored.charAt(stored.length() - 1);

        assertThat(cipher.decrypt(tampered, "TOSS", "owner-a")).isEmpty();
    }

    @Test
    void 키가_바뀌면_못_풀고_새로_발급받게_비워서_돌려준다() {
        String stored = cipher.encrypt(TOKEN, "TOSS", "owner-a");

        assertThat(new TokenCipher(OTHER_KEY).decrypt(stored, "TOSS", "owner-a")).isEmpty();
        assertThat(new TokenCipher("").decrypt(stored, "TOSS", "owner-a")).isEmpty();
    }

    @Test
    void 키를_넣기_전에_저장된_평문은_그대로_읽는다() {
        // 키를 처음 넣은 날 DB 에 남아 있던 토큰. 다음 저장 때 암호문으로 바뀐다
        assertThat(cipher.decrypt(TOKEN, "TOSS", "owner-a")).contains(TOKEN);
    }

    @Test
    void 키가_없으면_평문으로_저장한다() {
        TokenCipher none = new TokenCipher("");

        assertThat(none.enabled()).isFalse();
        assertThat(none.encrypt(TOKEN, "TOSS", "owner-a")).isEqualTo(TOKEN);
    }

    @Test
    void 키_모양이_틀리면_기동할_때_바로_알린다() {
        assertThatThrownBy(() -> new TokenCipher("짧은키"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("base64");
        assertThatThrownBy(() -> new TokenCipher(Base64.getEncoder().encodeToString(new byte[16])))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("32바이트");
    }
}
