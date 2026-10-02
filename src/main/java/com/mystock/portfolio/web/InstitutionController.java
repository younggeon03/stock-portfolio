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
     * 내 포트폴리오와 기관 10곳의 겹침 순위. h=AAPL:30,NVDA:20 (티커:비중, 비중은 아무 단위나 — 합계를 100% 로 맞춘다).
     * GET 이라 주소를 공유하면 같은 결과가 나온다. 입력은 저장하지 않는다. 13F 는 미국 주식만 있다.
     */
    @GetMapping("/overlap")
    public List<InstitutionPortfolioService.OverlapSummary> overlap(@RequestParam("h") String holdings) {
        return portfolioService.overlapAll(parseHoldings(holdings));
    }

    /** 기관 하나와의 겹침: 같이 가진 종목, 나만 가진 종목, 그 기관 상위 종목 중 내게 없는 것 */
    @GetMapping("/{cik}/overlap")
    public ResponseEntity<InstitutionPortfolioService.OverlapDetail> overlapOne(
            @PathVariable long cik, @RequestParam("h") String holdings) {
        return portfolioService.overlapOne(cik, parseHoldings(holdings))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** 한 번에 받는 종목 수. 공개 주소라 아주 긴 입력으로 서버를 괴롭히지 못하게 막는다 */
    static final int MAX_HOLDINGS = 50;
    private static final java.util.regex.Pattern TICKER = java.util.regex.Pattern.compile("[A-Za-z0-9.\\-/]{1,12}");

    /** "AAPL:30,NVDA:20" → 목록. 모양이 틀리면 400 (무엇이 틀렸는지 알려준다) */
    static List<com.mystock.portfolio.service.institution.Overlap.Mine> parseHoldings(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("비교할 종목이 없습니다. 예: AAPL:30,NVDA:20");
        }
        String[] parts = raw.split(",");
        if (parts.length > MAX_HOLDINGS) {
            throw new IllegalArgumentException("종목은 " + MAX_HOLDINGS + "개까지 넣을 수 있습니다");
        }
        List<com.mystock.portfolio.service.institution.Overlap.Mine> out = new java.util.ArrayList<>();
        for (String part : parts) {
            String p = part.strip();
            if (p.isEmpty()) {
                continue;
            }
            String[] kv = p.split(":");
            String ticker = kv[0].strip();
            if (!TICKER.matcher(ticker).matches()) {
                throw new IllegalArgumentException("티커 모양이 아닙니다: " + ticker);
            }
            double weight = 1;
            if (kv.length > 1) {
                try {
                    weight = Double.parseDouble(kv[1].strip().replace("%", ""));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("비중이 숫자가 아닙니다: " + p);
                }
            }
            if (!(weight > 0) || weight > 1e9) {
                throw new IllegalArgumentException("비중은 0보다 커야 합니다: " + p);
            }
            out.add(new com.mystock.portfolio.service.institution.Overlap.Mine(ticker, weight));
        }
        if (out.isEmpty()) {
            throw new IllegalArgumentException("비교할 종목이 없습니다. 예: AAPL:30,NVDA:20");
        }
        return out;
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
