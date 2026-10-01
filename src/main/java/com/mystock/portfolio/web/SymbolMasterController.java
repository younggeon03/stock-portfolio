package com.mystock.portfolio.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.mystock.portfolio.service.symbol.SymbolMasterService;
import com.mystock.portfolio.service.symbol.SymbolMatch;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 종목 마스터 관리.
 *
 * 스크린샷에서 읽은 "현대차" 를 005380 으로 바꾸려면 종목명-코드 표가 필요하다.
 * 그 표를 토스에서 받아와 DB 에 저장해두는 기능이다.
 *
 * 토스 문서가 "하루 1회 조회 후 로컬 캐싱" 을 권장하므로 자동 갱신하지 않고
 * 필요할 때 버튼으로 갱신한다.
 */
@Tag(name = "종목 마스터", description = "종목명을 종목코드로 바꾼다. 스크린샷 등록이 이걸 쓴다")
@RestController
@RequestMapping("/api/symbols")
public class SymbolMasterController {

    private final SymbolMasterService service;

    public SymbolMasterController(SymbolMasterService service) {
        this.service = service;
    }

    /** 종목 마스터에 몇 건이 들어있는지 */
    @GetMapping("/status")
    public Map<String, Object> status() {
        long count = service.size();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", count);
        result.put("ready", count > 0);
        result.put("message", count > 0
                ? count + "개 종목을 알고 있습니다."
                : "종목 목록이 비어 있습니다. 갱신을 먼저 실행하세요.");
        return result;
    }

    /**
     * 토스에서 전체 종목을 받아와 갱신한다.
     *
     * 마켓 7개(KOSPI, KOSDAQ, NYSE, NASDAQ, AMEX, KR_ETC, US_ETC)를 순서대로 부르므로
     * 수십 초 걸릴 수 있다. 한 마켓이 실패해도 나머지는 저장한다.
     */
    @PostMapping("/refresh")
    public Map<String, Object> refresh() {
        Map<String, Integer> perMarket = service.refreshAll();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("perMarket", perMarket);
        result.put("total", service.size());
        result.put("message", "종목 목록을 갱신했습니다. (-1 은 그 마켓 조회에 실패했다는 뜻입니다)");
        return result;
    }

    /**
     * 이름으로 종목을 찾는다. 검수 화면에서 종목을 직접 고를 때 쓴다.
     *
     * 예: /api/symbols/search?q=현대차
     */
    @GetMapping("/search")
    public SymbolMatch search(@RequestParam String q) {
        if (q == null || q.isBlank()) {
            throw new IllegalArgumentException("검색어(q)가 필요합니다.");
        }
        return service.findByName(q);
    }
}
