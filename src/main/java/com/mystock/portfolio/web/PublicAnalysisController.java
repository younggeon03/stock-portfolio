package com.mystock.portfolio.web;

import com.mystock.portfolio.service.analysis.CompanyAnalysisResponse;
import com.mystock.portfolio.service.analysis.CompanyAnalysisService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/**
 * 공개 기업분석 화면이 읽는 AI 기업분석. 로그인 없이 열린다(/api/public/**).
 *
 * 나의 포트폴리오의 기업분석과 같은 저장소를 읽지만, 내 평단가·수량·손익이 들어간 분석은 내보내지 않는다.
 * 새 분석을 돌리는(돈이 드는) POST 는 여기 없다. 그건 로그인 뒤의 /api/analysis/{symbol} 이다.
 */
@Tag(name = "공개 기업분석", description = "안 가진 종목을 현재가만으로 분석한 AI 기업분석. 읽기만, 무료, 로그인 없음")
@RestController
public class PublicAnalysisController {

    private final CompanyAnalysisService analysisService;

    public PublicAnalysisController(CompanyAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    /** 예: /api/public/analysis/MSFT. 공개할 분석이 없으면 status NONE */
    @GetMapping("/api/public/analysis/{symbol}")
    public CompanyAnalysisResponse get(@PathVariable String symbol) {
        return analysisService.findPublic(symbol.trim().toUpperCase(Locale.ROOT));
    }
}
