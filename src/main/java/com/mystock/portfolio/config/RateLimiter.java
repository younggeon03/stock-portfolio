package com.mystock.portfolio.config;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 고정 창(1분) 요청 제한. 키(IP 등)마다 이번 분에 몇 번 왔는지 센다.
 *
 * 더 정교한 방식(토큰 버킷, 레디스 공유)도 있지만 서버가 한 대라 메모리로 충분하다.
 * 분이 바뀌는 순간 몰리면 최대 두 배까지 지나갈 수 있는데, 공개 조회를 막는 목적엔 문제없다.
 * 시계를 받아서 테스트에서 시간을 움직일 수 있게 했다.
 */
public class RateLimiter {

    private final int perMinute;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimiter(int perMinute, Clock clock) {
        this.perMinute = perMinute;
        this.clock = clock;
    }

    /** 이번 요청을 받아도 되나. 안 되면 다음 분까지 남은 초를 함께 */
    public Decision tryAcquire(String key) {
        long minute = clock.millis() / 60_000;
        Window w = windows.compute(key, (k, old) -> old == null || old.minute != minute ? new Window(minute) : old);
        int n = w.count.incrementAndGet();
        if (windows.size() > 50_000) {
            // 지나간 분의 창을 치운다. 키가 무한히 쌓이지 않게
            windows.entrySet().removeIf(e -> e.getValue().minute != minute);
        }
        long retryAfter = 60 - (clock.millis() / 1000) % 60;
        return new Decision(n <= perMinute, Math.max(1, retryAfter));
    }

    public record Decision(boolean allowed, long retryAfterSeconds) {
    }

    private static final class Window {
        final long minute;
        final AtomicInteger count = new AtomicInteger();

        Window(long minute) {
            this.minute = minute;
        }
    }
}
