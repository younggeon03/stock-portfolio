package com.mystock.portfolio.external.anthropic;

import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.Usage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Claude 호출 지표. SDK 를 부르지 않고 응답만 흉내 낸다 */
class ClaudeMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ClaudeMetrics metrics = new ClaudeMetrics(registry);

    private static Message message(long input, long cacheWrite, long cacheRead, long output) {
        Usage usage = mock(Usage.class);
        when(usage.inputTokens()).thenReturn(input);
        when(usage.cacheCreationInputTokens()).thenReturn(Optional.of(cacheWrite));
        when(usage.cacheReadInputTokens()).thenReturn(Optional.of(cacheRead));
        when(usage.outputTokens()).thenReturn(output);
        Message m = mock(Message.class);
        when(m.usage()).thenReturn(usage);
        return m;
    }

    private double tokens(String type) {
        var c = registry.find("claude.tokens").tag("kind", "analysis").tag("type", type).counter();
        return c == null ? 0 : c.count();
    }

    @Test
    void 성공하면_호출과_토큰을_종류별로_센다() {
        metrics.record("analysis", "claude-opus-5", () -> message(1200, 300, 9000, 2500));
        metrics.record("analysis", "claude-opus-5", () -> message(100, 0, 9000, 500));

        assertThat(registry.find("claude.requests").tag("outcome", "success").timer().count()).isEqualTo(2);
        assertThat(tokens("input")).isEqualTo(1300);
        assertThat(tokens("cache_read")).isEqualTo(18000);   // 캐싱이 살아 있으면 0 이 아니다
        assertThat(tokens("output")).isEqualTo(3000);
    }

    @Test
    void 실패하면_error_로_재고_예외는_그대로() {
        assertThatThrownBy(() -> metrics.record("news", "claude-haiku-4-5", () -> {
            throw new IllegalStateException("크레딧 부족");
        })).hasMessage("크레딧 부족");

        assertThat(registry.find("claude.requests").tag("kind", "news").tag("outcome", "error").timer().count()).isEqualTo(1);
        assertThat(registry.find("claude.tokens").tag("kind", "news").counter()).isNull();
    }
}
