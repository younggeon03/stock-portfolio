package com.mystock.portfolio.web;

import com.mystock.portfolio.external.dart.DartFinancialService;
import com.mystock.portfolio.external.edgar.EdgarFinancialService;
import com.mystock.portfolio.external.filing.CompanyFinancials;
import com.mystock.portfolio.external.filing.FilingService;
import com.mystock.portfolio.external.toss.TossMarketDataService;
import com.mystock.portfolio.external.toss.dto.TossPrice;
import com.mystock.portfolio.external.toss.dto.TossStockInfo;
import com.mystock.portfolio.service.filing.FilingPrefetchService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 기업분석에 들어갈 공시 재무를 미리 본다. 한국은 DART, 미국은 SEC EDGAR.
 *
 * ★ 왜 따로 여는가
 * 기업분석은 1회 800원 넘게 든다. 거기 들어갈 재무 숫자가 맞는지는 클로드를 부르지 않고도 확인할 수 있어야 한다.
 * DART·EDGAR·토스 모두 무료라 이 주소는 0원이다.
 */
@Tag(name = "공시 재무", description = "DART·SEC EDGAR 원본 재무와 앱이 계산한 비율. 무료. 기업분석 프롬프트에 그대로 들어가는 값이다")
@RestController
@RequestMapping("/api/financials")
public class FinancialsController {

    private static final Logger log = LoggerFactory.getLogger(FinancialsController.class);

    private final FilingService filingService;
    private final DartFinancialService dartFinancialService;
    private final EdgarFinancialService edgarFinancialService;
    private final TossMarketDataService marketDataService;
    private final FilingPrefetchService prefetchService;

    public FinancialsController(FilingService filingService, DartFinancialService dartFinancialService,
                                EdgarFinancialService edgarFinancialService,
                                TossMarketDataService marketDataService,
                                FilingPrefetchService prefetchService) {
        this.filingService = filingService;
        this.dartFinancialService = dartFinancialService;
        this.edgarFinancialService = edgarFinancialService;
        this.marketDataService = marketDataService;
        this.prefetchService = prefetchService;
    }

    /**
     * 국내 6자리 코드면 DART, 아니면 미국 티커로 보고 EDGAR.
     * 토스가 막혀 있으면(403) 현재가 없이 재무만 나가고 PER·PBR 은 비어 있다.
     * 예: /api/financials/005930, /api/financials/AVGO
     */
    @GetMapping("/{symbol}")
    public ResponseEntity<CompanyFinancials> financials(@PathVariable String symbol) {
        BigDecimal shares = null;
        BigDecimal price = null;
        boolean fund = false;
        try {
            TossStockInfo info = marketDataService.stockInfo(symbol);
            if (info != null) {
                shares = info.sharesOutstanding();
                fund = info.isFund();
            }
            List<TossPrice> prices = marketDataService.prices(List.of(symbol));
            price = prices.isEmpty() ? null : prices.get(0).lastPrice();
        } catch (Exception e) {
            log.warn("{} 토스 조회 실패, 재무만 보여줍니다: {}", symbol, e.getMessage());
        }
        return filingService.find(symbol, FilingService.guessCountry(symbol), fund, shares, price)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** 새로 상장한 종목이 안 잡힐 때. 두 매핑 파일(DART 3.6MB, SEC 800KB)을 다시 받는다 */
    @PostMapping("/mappings/refresh")
    public Map<String, Integer> refreshMappings() {
        return Map.of(
                "dartListed", dartFinancialService.refreshCorpCodes(),
                "secTickers", edgarFinancialService.refreshCiks());
    }

    /**
     * 야간 배치(매일 06:00)를 지금 한 번 돌린다. 보유·분석해 둔 종목의 공시 재무를 다시 받아 저장한다.
     * 종목 수에 따라 수십 초~몇 분 걸리고 끝나야 응답한다. 0원
     */
    @PostMapping("/prefetch")
    public FilingPrefetchService.Result prefetch() {
        return prefetchService.run();
    }
}
