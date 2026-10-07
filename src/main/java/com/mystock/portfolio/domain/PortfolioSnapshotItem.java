package com.mystock.portfolio.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 스냅샷 날의 종목 한 줄(증권사를 합친 뒤). 날짜·종목당 한 줄 */
@Entity
@Table(name = "portfolio_snapshot_item")
public class PortfolioSnapshotItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 20, scale = 6)
    private BigDecimal quantity;

    /** 거래통화 기준 */
    @Column(name = "last_price", nullable = false, precision = 20, scale = 4)
    private BigDecimal lastPrice;

    @Column(name = "market_value_krw", nullable = false, precision = 20, scale = 2)
    private BigDecimal marketValueKrw;

    @Column(name = "purchase_krw", nullable = false, precision = 20, scale = 2)
    private BigDecimal purchaseKrw;

    @Column(name = "weight_percent", nullable = false, precision = 7, scale = 2)
    private BigDecimal weightPercent;

    protected PortfolioSnapshotItem() {
    }

    public PortfolioSnapshotItem(LocalDate snapshotDate, String symbol, String name, String currency,
                                 BigDecimal quantity, BigDecimal lastPrice, BigDecimal marketValueKrw,
                                 BigDecimal purchaseKrw, BigDecimal weightPercent) {
        this.snapshotDate = snapshotDate;
        this.symbol = symbol;
        this.name = name;
        this.currency = currency;
        this.quantity = quantity;
        this.lastPrice = lastPrice;
        this.marketValueKrw = marketValueKrw;
        this.purchaseKrw = purchaseKrw;
        this.weightPercent = weightPercent;
    }

    public LocalDate getSnapshotDate() {
        return snapshotDate;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getName() {
        return name;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getLastPrice() {
        return lastPrice;
    }

    public BigDecimal getMarketValueKrw() {
        return marketValueKrw;
    }

    public BigDecimal getPurchaseKrw() {
        return purchaseKrw;
    }

    public BigDecimal getWeightPercent() {
        return weightPercent;
    }
}
