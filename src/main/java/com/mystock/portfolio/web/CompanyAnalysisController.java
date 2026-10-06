package com.mystock.portfolio.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.mystock.portfolio.service.analysis.CompanyAnalysisResponse;
import com.mystock.portfolio.service.analysis.CompanyAnalysisService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * 기업분석 조회와 실행.
 *
 * ★★ 이 앱은 주문을 내지 않는다. 분석 결과는 참고자료일 뿐이다. ★★
 *
 * 확인용 주소:
 *   GET  /api/analysis/NVDA              저장된 분석 조회 (클로드를 부르지 않음, 즉시 응답)
 *   GET  /api/analysis/status?symbols=.. 여러 종목의 분석 유무만 가볍게
 *   POST /api/analysis/NVDA              분석 시작 (백그라운드로 돌고 바로 RUNNING 반환)
 *   POST /api/analysis/NVDA?refresh=true 캐시를 무시하고 다시 분석
 */
@Tag(name = "기업분석", description = "클로드가 웹검색으로 조사한다. POST 만 돈이 나간다")
@RestController
@RequestMapping("/api/analysis")
public class CompanyAnalysisController {

    private final CompanyAnalysisService analysisService;

    public CompanyAnalysisController(CompanyAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    /**
     * 저장된 분석을 돌려준다.
     * 분석한 적이 없으면 status 가 NONE 으로 온다. 클로드를 부르지 않으므로 즉시 응답한다.
     */
    @GetMapping("/{symbol}")
    public CompanyAnalysisResponse get(@PathVariable String symbol) {
        return analysisService.find(symbol.trim().toUpperCase());
    }

    /** 표의 "기업분석" 칸을 한 번에 채우기 위한 가벼운 목록 조회 */
    @GetMapping("/status")
    public List<CompanyAnalysisResponse> status(@RequestParam String symbols) {
        List<String> parsed = Arrays.stream(symbols.split(","))
                .map(String::trim)
                .map(String::toUpperCase)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();

        if (parsed.isEmpty()) {
            throw new IllegalArgumentException("symbols 파라미터가 필요합니다.");
        }
        return analysisService.findAll(parsed);
    }

    /**
     * 분석을 시작한다.
     *
     * 클로드가 웹검색을 하며 답을 만드는 데 몇 분이 걸린다.
     * 그동안 브라우저를 붙잡아두면 끊기기 쉬우므로, 여기서는 바로 응답하고
     * 화면이 몇 초 간격으로 GET 을 다시 불러 상태를 확인한다.
     *
     * @param refresh true 면 저장된 분석이 있어도 다시 분석한다
     */
    @PostMapping("/{symbol}")
    public CompanyAnalysisResponse start(@PathVariable String symbol,
                                         @RequestParam(defaultValue = "false") boolean refresh,
                                         @RequestHeader(value = "X-Owner-Key", required = false) String ownerKey) {
        return analysisService.start(symbol.trim().toUpperCase(), refresh, ownerKey);
    }

    /**
     * 판단만 새로 쓴다. 저장된 조사(섹션)는 그대로 두고 판정·요약·내 위치·위험만 웹검색 없이 다시 쓴다.
     * 조사가 보관 일수를 넘겼으면 400 이다. 그때는 위의 refresh=true 로 전체를 다시 분석한다.
     * 돈이 나간다(전체 분석보다 훨씬 적게). 시작과 상태 확인 방식은 위와 같다
     */
    @PostMapping("/{symbol}/reassess")
    public CompanyAnalysisResponse reassess(@PathVariable String symbol,
                                            @RequestHeader(value = "X-Owner-Key", required = false) String ownerKey) {
        return analysisService.reassess(symbol.trim().toUpperCase(), ownerKey);
    }
}
