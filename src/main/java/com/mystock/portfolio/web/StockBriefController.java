package com.mystock.portfolio.web;

import com.mystock.portfolio.service.stock.StockBriefService;
import com.mystock.portfolio.service.stock.StockBriefService.StockBriefView;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/**
 * 공개 종목 창. 기관 포트폴리오 화면에서 종목을 누르면 열린다.
 *
 * /api/public/** 라서 로그인 없이 열리고 호출 한도(IP 당 분당 120번)가 걸린다.
 * 내 잔고·판정·유료 분석은 여기에 넣지 않는다. 공시 재무·13F 같은 사실과 앱이 규칙으로 만든 문장만.
 */
@Tag(name = "공개 종목 창", description = "미국 종목 하나의 공시 재무, 읽을 점, 기관 10곳의 보유 변화. 무료, 로그인 없음")
@RestController
public class StockBriefController {

    private final StockBriefService service;

    public StockBriefController(StockBriefService service) {
        this.service = service;
    }

    /** 예: /api/public/stocks/MSFT */
    @GetMapping("/api/public/stocks/{ticker}")
    public ResponseEntity<StockBriefView> brief(@PathVariable String ticker) {
        String t = ticker.trim().toUpperCase(Locale.ROOT);
        // 아무 문자열이나 받아 캐시 키로 쓰면 캐시를 쓰레기로 채울 수 있다. 티커 모양만 받는다
        if (!StockBriefService.validTicker(t)) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(service.brief(t));
    }
}
