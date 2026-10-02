package com.mystock.portfolio.config;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/** 분당 요청 제한. 시계를 직접 움직여 시험한다 */
class RateLimiterTest {

    private final AtomicLong now = new AtomicLong(Instant.parse("2026-10-02T10:00:10Z").toEpochMilli());
    private final Clock clock = new Clock() {
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(now.get()); }
    };

    @Test
    void 한도까지는_받고_넘으면_막으며_다음_분까지_남은_초를_알려준다() {
        RateLimiter limiter = new RateLimiter(3, clock);

        assertThat(limiter.tryAcquire("1.2.3.4").allowed()).isTrue();
        assertThat(limiter.tryAcquire("1.2.3.4").allowed()).isTrue();
        assertThat(limiter.tryAcquire("1.2.3.4").allowed()).isTrue();
        RateLimiter.Decision fourth = limiter.tryAcquire("1.2.3.4");

        assertThat(fourth.allowed()).isFalse();
        assertThat(fourth.retryAfterSeconds()).isEqualTo(50);   // 10:00:10 → 10:01:00
    }

    @Test
    void IP_마다_따로_센다() {
        RateLimiter limiter = new RateLimiter(1, clock);

        assertThat(limiter.tryAcquire("1.1.1.1").allowed()).isTrue();
        assertThat(limiter.tryAcquire("2.2.2.2").allowed()).isTrue();
        assertThat(limiter.tryAcquire("1.1.1.1").allowed()).isFalse();
    }

    @Test
    void 분이_바뀌면_다시_받는다() {
        RateLimiter limiter = new RateLimiter(1, clock);
        limiter.tryAcquire("1.1.1.1");
        assertThat(limiter.tryAcquire("1.1.1.1").allowed()).isFalse();

        now.addAndGet(60_000);

        assertThat(limiter.tryAcquire("1.1.1.1").allowed()).isTrue();
    }
}
