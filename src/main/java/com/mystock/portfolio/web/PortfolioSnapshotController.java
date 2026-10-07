package com.mystock.portfolio.web;

import com.mystock.portfolio.domain.PortfolioSnapshot;
import com.mystock.portfolio.domain.PortfolioSnapshotItem;
import com.mystock.portfolio.domain.ReconciliationResult;
import com.mystock.portfolio.service.snapshot.PortfolioSnapshotService;
import com.mystock.portfolio.service.snapshot.PortfolioSnapshotStore;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 장 마감 스냅샷(매일 평가금액 이력)과 대사 결과. 내 잔고라 로그인 뒤에만 열린다(공개 경로가 아님).
 *
 *   GET  /api/portfolio/snapshots?days=180   그래프용 날짜별 합계
 *   GET  /api/portfolio/snapshots/2026-10-07 그날 종목별 내역과 대사 줄
 *   POST /api/portfolio/snapshots            오늘 것을 지금 다시 찍는다(증권사 조회만, 0원)
 */
@Tag(name = "스냅샷과 대사", description = "장 마감 뒤 하루 한 번 남기는 평가금액 이력, 증권사 합계와 맞춰 본 결과. 0원")
@RestController
@RequestMapping("/api/portfolio/snapshots")
public class PortfolioSnapshotController {

    private final PortfolioSnapshotStore store;
    private final PortfolioSnapshotService service;

    public PortfolioSnapshotController(PortfolioSnapshotStore store, PortfolioSnapshotService service) {
        this.store = store;
        this.service = service;
    }

    /** 날짜별 합계 한 줄 */
    public record Point(LocalDate date, LocalDateTime takenAt, BigDecimal totalValueKrw, BigDecimal totalPurchaseKrw,
                        BigDecimal profitLossKrw, BigDecimal profitRatePercent, int itemCount, int mismatchCount) {

        static Point of(PortfolioSnapshot s) {
            return new Point(s.getSnapshotDate(), s.getTakenAt(), s.getTotalValueKrw(), s.getTotalPurchaseKrw(),
                    s.getProfitLossKrw(), s.getProfitRatePercent(), s.getItemCount(), s.getMismatchCount());
        }
    }

    public record Item(String symbol, String name, String currency, BigDecimal quantity, BigDecimal lastPrice,
                       BigDecimal marketValueKrw, BigDecimal purchaseKrw, BigDecimal weightPercent) {
    }

    public record Reconciliation(String broker, String scope, String currency, BigDecimal brokerTotal,
                                 BigDecimal appSum, BigDecimal difference, boolean matched) {
    }

    public record Detail(Point summary, List<Item> items, List<Reconciliation> reconciliation) {
    }

    /** @param days 오늘부터 며칠 전까지. 1~1830 (5년) */
    @GetMapping
    public List<Point> history(@RequestParam(defaultValue = "180") int days) {
        int span = Math.max(1, Math.min(days, 1830));
        return store.since(LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).minusDays(span))
                .stream().map(Point::of).toList();
    }

    @GetMapping("/{date}")
    public Detail detail(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        PortfolioSnapshot s = store.find(date)
                .orElseThrow(() -> new IllegalArgumentException(date + " 스냅샷이 없습니다."));
        List<Item> items = store.itemsOn(date).stream().map(PortfolioSnapshotController::item).toList();
        List<Reconciliation> recon = store.reconciliationOn(date).stream()
                .map(PortfolioSnapshotController::reconciliation).toList();
        return new Detail(Point.of(s), items, recon);
    }

    /** 오늘 것을 지금 찍는다. 이미 있어도 다시 찍는다(그날 한 벌만 남는다) */
    @PostMapping
    public PortfolioSnapshotService.Result takeNow() {
        return service.take(true);
    }

    private static Item item(PortfolioSnapshotItem i) {
        return new Item(i.getSymbol(), i.getName(), i.getCurrency(), i.getQuantity(), i.getLastPrice(),
                i.getMarketValueKrw(), i.getPurchaseKrw(), i.getWeightPercent());
    }

    private static Reconciliation reconciliation(ReconciliationResult r) {
        return new Reconciliation(r.getBroker(), r.getScope(), r.getCurrency(), r.getBrokerTotal(),
                r.getAppSum(), r.getDifference(), r.isMatched());
    }
}
