package com.mystock.portfolio.service.snapshot;

import com.mystock.portfolio.domain.PortfolioSnapshot;
import com.mystock.portfolio.domain.PortfolioSnapshotItem;
import com.mystock.portfolio.domain.PortfolioSnapshotItemRepository;
import com.mystock.portfolio.domain.PortfolioSnapshotRepository;
import com.mystock.portfolio.domain.ReconciliationResult;
import com.mystock.portfolio.domain.ReconciliationResultRepository;
import com.mystock.portfolio.service.UnifiedPortfolioView;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 스냅샷을 DB 에 쓰고 읽는다.
 *
 * ★ 멱등하게 쓴다 (같은 날 두 번 돌아도 한 벌)
 * 한 트랜잭션 안에서: 그날 합계 줄은 있으면 고치고 없으면 만든다(upsert) → 그날 종목·대사 줄은 지우고 새로 넣는다.
 * 중간에 실패하면 통째로 되돌아가서 "합계는 새것, 종목은 옛것" 같은 반쪽 상태가 남지 않는다.
 * 재시도(16·18·20시)와 수동 실행이 같은 날을 여러 번 써도 결과는 마지막 한 번의 것이다.
 */
@Component
public class PortfolioSnapshotStore {

    private final PortfolioSnapshotRepository snapshots;
    private final PortfolioSnapshotItemRepository items;
    private final ReconciliationResultRepository reconciliations;

    public PortfolioSnapshotStore(PortfolioSnapshotRepository snapshots, PortfolioSnapshotItemRepository items,
                                  ReconciliationResultRepository reconciliations) {
        this.snapshots = snapshots;
        this.items = items;
        this.reconciliations = reconciliations;
    }

    @Transactional
    public PortfolioSnapshot save(LocalDate date, LocalDateTime takenAt, UnifiedPortfolioView view,
                                  List<Reconciler.Line> lines) {
        int mismatches = (int) lines.stream().filter(l -> !l.matched()).count();
        PortfolioSnapshot snapshot = snapshots.findById(date).orElseGet(() -> new PortfolioSnapshot(date));
        snapshot.update(takenAt, view.totalValueKrw(), view.totalPurchaseKrw(), view.totalProfitLossKrw(),
                view.totalProfitRatePercent(), view.items().size(), mismatches);
        snapshots.save(snapshot);

        items.deleteAllOn(date);
        items.saveAll(view.items().stream().map(i -> new PortfolioSnapshotItem(date, i.symbol(), i.name(),
                i.currency(), i.quantity(), i.lastPrice(), i.marketValueKrw(), i.purchaseKrw(),
                i.weightPercent())).toList());

        reconciliations.deleteAllOn(date);
        reconciliations.saveAll(lines.stream().map(l -> new ReconciliationResult(date, l.broker().name(),
                l.scope(), l.currency(), l.brokerTotal(), l.appSum(), l.difference(), l.matched())).toList());
        return snapshot;
    }

    @Transactional(readOnly = true)
    public boolean exists(LocalDate date) {
        return snapshots.existsById(date);
    }

    @Transactional(readOnly = true)
    public List<PortfolioSnapshot> since(LocalDate from) {
        return snapshots.findBySnapshotDateGreaterThanEqualOrderBySnapshotDate(from);
    }

    @Transactional(readOnly = true)
    public Optional<PortfolioSnapshot> find(LocalDate date) {
        return snapshots.findById(date);
    }

    @Transactional(readOnly = true)
    public List<PortfolioSnapshotItem> itemsOn(LocalDate date) {
        return items.findBySnapshotDateOrderByMarketValueKrwDesc(date);
    }

    @Transactional(readOnly = true)
    public List<ReconciliationResult> reconciliationOn(LocalDate date) {
        return reconciliations.findBySnapshotDateOrderByBrokerAscScopeAsc(date);
    }
}
