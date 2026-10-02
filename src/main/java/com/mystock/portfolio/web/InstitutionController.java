package com.mystock.portfolio.web;

import com.mystock.portfolio.external.thirteenf.ThirteenFSyncService;
import com.mystock.portfolio.service.institution.InstitutionPortfolioService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 기관 투자자 포트폴리오(SEC 13F). 공개 자료라 내 계좌와 무관하고 0원이다.
 *
 * 조회는 DB 에 받아 둔 것만 읽는다. 받는 건 매일 아침 배치가 하고, 지금 당장 받고 싶으면 POST /sync.
 */
@Tag(name = "기관 포트폴리오 (13F)", description = "국민연금·버크셔 등이 SEC 에 낸 분기별 미국 주식 보유. 무료. 분기말 뒤 최대 45일 늦은 자료다")
@RestController
@RequestMapping("/api/institutions")
public class InstitutionController {

    private final InstitutionPortfolioService portfolioService;
    private final ThirteenFSyncService syncService;

    public InstitutionController(InstitutionPortfolioService portfolioService, ThirteenFSyncService syncService) {
        this.portfolioService = portfolioService;
        this.syncService = syncService;
    }

    /** 따라가는 기관과 받아 둔 최신 분기 */
    @GetMapping
    public List<InstitutionPortfolioService.InstitutionView> institutions() {
        return portfolioService.list();
    }

    /** 한 기관의 보유. period(분기말, 예: 2026-06-30)가 없으면 최신 분기 */
    @GetMapping("/{cik}/holdings")
    public ResponseEntity<InstitutionPortfolioService.HoldingsView> holdings(
            @PathVariable long cik,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate period) {
        return portfolioService.holdings(cik, period)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 한 기관의 분기 변화: 새로 산 · 늘린 · 줄인 · 다 판 종목. 주식 수 기준이다(금액은 주가만 올라도 늘어서).
     * 액면분할로 주식 수만 늘어난 종목은 SPLIT 으로 따로 둔다. period 가 없으면 최신 분기와 그 앞 분기
     */
    @GetMapping("/{cik}/changes")
    public ResponseEntity<InstitutionPortfolioService.ChangesView> changes(
            @PathVariable long cik,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate period) {
        return portfolioService.changes(cik, period)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 여러 기관이 같은 분기에 같이 늘린(bought) · 줄인(sold) 종목. 두 곳 이상만.
     * period 가 없으면 기관 절반 이상이 낸 가장 최근 분기
     */
    @GetMapping("/consensus")
    public InstitutionPortfolioService.ConsensusView consensus(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate period,
            @RequestParam(defaultValue = "30") int limit) {
        return portfolioService.consensus(period, Math.max(1, Math.min(limit, 100)));
    }

    /**
     * 지금 받기. 뒤에서 돌고 바로 돌아온다. 처음이면 10곳 × 8분기라 몇 분,
     * 티커 찾기까지 끝나려면 (노르웨이 중앙은행 때문에) 30분쯤 걸린다. 진행은 GET /sync 로 본다.
     */
    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> sync() {
        boolean started = syncService.startAsync();
        return ResponseEntity.accepted().body(Map.of("started", started,
                "message", started ? "받기 시작했습니다" : "이미 받는 중입니다"));
    }

    /** 마지막 받기 결과 */
    @GetMapping("/sync")
    public ThirteenFSyncService.Status syncStatus() {
        return syncService.status();
    }
}
