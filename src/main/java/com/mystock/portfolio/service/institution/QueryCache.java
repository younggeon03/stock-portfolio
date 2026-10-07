package com.mystock.portfolio.service.institution;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 13F 조회 결과를 들고 있는 작은 캐시. 오래 안 쓴 것부터 버린다(LRU). 시간으로 만료하지 않는다 —
 * 데이터가 바뀌는 때(13F 배치)를 정확히 알아서 그때 clear() 한다.
 *
 * 라이브러리(Caffeine)를 쓰지 않은 이유: 항목이 수십 개고 필요한 건 "크기 상한 + 통째로 비우기" 뿐이다.
 * 앱이 여러 대가 되면 이 캐시는 앱마다 따로라 Redis 같은 공용 캐시로 옮겨야 한다(docs/기술노트.md 1장).
 *
 * 같은 키를 동시에 처음 찾으면 둘 다 계산할 수 있다. 결과가 같고 하루에 몇 번 안 생겨서 막지 않는다
 * (계산 중에 잠금을 잡으면 느린 계산 하나가 다른 키 조회까지 막는다).
 */
final class QueryCache {

    private final Map<String, Object> map;

    QueryCache(int maxEntries) {
        this.map = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Object> eldest) {
                return size() > maxEntries;
            }
        };
    }

    @SuppressWarnings("unchecked")
    <T> T get(String key, Supplier<T> load) {
        synchronized (map) {
            Object hit = map.get(key);
            if (hit != null) {
                return (T) hit;
            }
        }
        T value = load.get();
        if (value != null) {
            synchronized (map) {
                map.put(key, value);
            }
        }
        return value;
    }

    void clear() {
        synchronized (map) {
            map.clear();
        }
    }

    int size() {
        synchronized (map) {
            return map.size();
        }
    }
}
