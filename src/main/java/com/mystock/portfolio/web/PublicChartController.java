package com.mystock.portfolio.web;

import com.mystock.portfolio.service.TossAnalysisService;
import com.mystock.portfolio.service.TossAnalysisView;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 공개 기업분석 화면의 캔들차트. 나의 포트폴리오의 /api/toss/chart 와 같은 데이터(일봉 200개 + 이평선)다.
 * 로그인 없이 열리므로(/api/public/**, IP 당 분당 120번) 두 가지를 더 한다.
 *
 * - 종목 모양만 받는다(미국 티커 또는 국내 6자리). 아무 문자열이나 토스에 넘기거나 캐시 키로 쓰지 않게
 * - 10분 캐시. 방문자마다 토스를 부르면 토스 호출 한도(429)에 걸려 내 포트폴리오 화면까지 시세가 끊긴다.
 *   일봉이라 10분 묵어도 그림이 달라지지 않는다(오늘 봉의 현재가만 조금 늦다)
 */
@Tag(name = "공개 차트", description = "종목 하나의 일봉 200개와 이동평균선. 무료, 로그인 없음, 10분 캐시")
@RestController
public class PublicChartController {

    static final Duration TTL = Duration.ofMinutes(10);
    static final int MAX_ENTRIES = 300;
    static final Pattern SYMBOL = Pattern.compile("[A-Z][A-Z0-9.-]{0,9}|\\d{6}");
    /** 120일선이 보이려면 200봉이 필요하다(TossAnalysisController 와 같은 이유) */
    private static final int DAYS = 200;

    private static final Logger log = LoggerFactory.getLogger(PublicChartController.class);

    private final TossAnalysisService analysisService;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public PublicChartController(TossAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    /** 예: /api/public/chart/MSFT */
    @GetMapping("/api/public/chart/{symbol}")
    public ResponseEntity<?> chart(@PathVariable String symbol) {
        String s = symbol.trim().toUpperCase(Locale.ROOT);
        if (!SYMBOL.matcher(s).matches()) {
            return ResponseEntity.badRequest().build();
        }
        Cached hit = cache.get(s);
        if (hit != null && hit.at().plus(TTL).isAfter(Instant.now())) {
            return ResponseEntity.ok(hit.chart());
        }
        TossAnalysisView.Chart chart;
        try {
            chart = analysisService.chart(s, DAYS);
        } catch (Exception e) {
            /*
             * ★ 토스 오류 문구를 그대로 내보내지 않는다.
             * 허용 IP 오류는 "지금 이 컴퓨터의 공인 IP 는 [...]" 처럼 서버 IP 와 조치 방법을 담고 있다.
             * 로그인 화면(/api/toss/chart)에서는 그게 맞지만 공개 주소에서는 방문자에게 운영 정보가 샌다.
             * 자세한 건 로그와 Grafana(외부 API 실패 칸)에서 본다. 실패는 캐시하지 않는다(풀리면 바로 그려지게)
             */
            log.warn("공개 차트 {} 실패: {}", s, e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "지금은 시세를 받을 수 없어 차트를 그리지 못했습니다. 잠시 뒤 다시 열어 주세요."));
        }
        if (cache.size() >= MAX_ENTRIES) {
            cache.clear();
        }
        cache.put(s, new Cached(Instant.now(), chart));
        return ResponseEntity.ok(chart);
    }

    private record Cached(Instant at, TossAnalysisView.Chart chart) {
    }
}
