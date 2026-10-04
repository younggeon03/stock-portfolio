package com.mystock.portfolio.service.institution;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** 13F 조회 캐시. 같은 키는 한 번만 계산하고, 상한을 넘으면 오래 안 쓴 것부터 버리고, 비우면 다시 계산한다 */
class QueryCacheTest {

    @Test
    void 같은_키는_한_번만_계산한다() {
        QueryCache cache = new QueryCache(10);
        AtomicInteger loads = new AtomicInteger();

        cache.get("consensus", () -> "결과" + loads.incrementAndGet());
        String second = cache.get("consensus", () -> "결과" + loads.incrementAndGet());

        assertThat(second).isEqualTo("결과1");
        assertThat(loads).hasValue(1);
    }

    @Test
    void 비우면_다시_계산한다() {
        QueryCache cache = new QueryCache(10);
        AtomicInteger loads = new AtomicInteger();
        cache.get("k", loads::incrementAndGet);

        cache.clear();   // 13F 배치가 끝났다
        cache.get("k", loads::incrementAndGet);

        assertThat(loads).hasValue(2);
    }

    @Test
    void 상한을_넘으면_오래_안_쓴_것부터_버린다() {
        QueryCache cache = new QueryCache(2);
        cache.get("a", () -> 1);
        cache.get("b", () -> 2);
        cache.get("a", () -> 99);   // a 를 최근에 씀
        cache.get("c", () -> 3);    // b 가 밀려남

        assertThat(cache.size()).isEqualTo(2);
        assertThat(cache.<Integer>get("a", () -> -1)).isEqualTo(1);
        assertThat(cache.<Integer>get("b", () -> -1)).isEqualTo(-1);
    }
}
