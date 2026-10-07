package com.mystock.portfolio.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 하루 한 번 남기는 보유 현황 합계. 기간별 수익률 그래프가 이 줄들을 잇는다.
 * 종목별 내역은 portfolio_snapshot_item, 대사 결과는 reconciliation_result 에 같은 날짜로 있다.
 *
 * 날짜가 키라서 같은 날 다시 찍으면 이 줄을 고쳐 쓴다(PortfolioSnapshotStore).
 */
@Entity
@Table(name = "portfolio_snapshot")
public class PortfolioSnapshot {

    @Id
    @Column(name = "snapshot_date")
    private LocalDate snapshotDate;

    @Column(name = "taken_at", nullable = false)
    private LocalDateTime takenAt;

    @Column(name = "total_value_krw", nullable = false, precision = 20, scale = 2)
    private BigDecimal totalValueKrw;

    @Column(name = "total_purchase_krw", nullable = false, precision = 20, scale = 2)
    private BigDecimal totalPurchaseKrw;

    @Column(name = "profit_loss_krw", nullable = false, precision = 20, scale = 2)
    private BigDecimal profitLossKrw;

    @Column(name = "profit_rate_percent", nullable = false, precision = 9, scale = 2)
    private BigDecimal profitRatePercent;

    @Column(name = "item_count", nullable = false)
    private int itemCount;

    /** 증권사 합계와 어긋난 줄 수 */
    @Column(name = "mismatch_count", nullable = false)
    private int mismatchCount;

    protected PortfolioSnapshot() {
    }

    public PortfolioSnapshot(LocalDate snapshotDate) {
        this.snapshotDate = snapshotDate;
    }

    public void update(LocalDateTime takenAt, BigDecimal totalValueKrw, BigDecimal totalPurchaseKrw,
                       BigDecimal profitLossKrw, BigDecimal profitRatePercent, int itemCount, int mismatchCount) {
        this.takenAt = takenAt;
        this.totalValueKrw = totalValueKrw;
        this.totalPurchaseKrw = totalPurchaseKrw;
        this.profitLossKrw = profitLossKrw;
        this.profitRatePercent = profitRatePercent;
        this.itemCount = itemCount;
        this.mismatchCount = mismatchCount;
    }

    public LocalDate getSnapshotDate() {
        return snapshotDate;
    }

    public LocalDateTime getTakenAt() {
        return takenAt;
    }

    public BigDecimal getTotalValueKrw() {
        return totalValueKrw;
    }

    public BigDecimal getTotalPurchaseKrw() {
        return totalPurchaseKrw;
    }

    public BigDecimal getProfitLossKrw() {
        return profitLossKrw;
    }

    public BigDecimal getProfitRatePercent() {
        return profitRatePercent;
    }

    public int getItemCount() {
        return itemCount;
    }

    public int getMismatchCount() {
        return mismatchCount;
    }
}
