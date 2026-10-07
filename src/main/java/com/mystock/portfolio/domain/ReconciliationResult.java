package com.mystock.portfolio.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 대사 한 줄: 그날 증권사가 밝힌 합계와 앱이 같은 범위를 더한 값.
 * 날짜·증권사·범위(KRW, USD, 국내, 해외(원화))당 한 줄
 */
@Entity
@Table(name = "reconciliation_result")
public class ReconciliationResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(nullable = false, length = 20)
    private String broker;

    @Column(nullable = false, length = 20)
    private String scope;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "broker_total", nullable = false, precision = 20, scale = 4)
    private BigDecimal brokerTotal;

    @Column(name = "app_sum", nullable = false, precision = 20, scale = 4)
    private BigDecimal appSum;

    /** 앱 − 증권사 */
    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal difference;

    @Column(nullable = false)
    private boolean matched;

    protected ReconciliationResult() {
    }

    public ReconciliationResult(LocalDate snapshotDate, String broker, String scope, String currency,
                                BigDecimal brokerTotal, BigDecimal appSum, BigDecimal difference, boolean matched) {
        this.snapshotDate = snapshotDate;
        this.broker = broker;
        this.scope = scope;
        this.currency = currency;
        this.brokerTotal = brokerTotal;
        this.appSum = appSum;
        this.difference = difference;
        this.matched = matched;
    }

    public LocalDate getSnapshotDate() {
        return snapshotDate;
    }

    public String getBroker() {
        return broker;
    }

    public String getScope() {
        return scope;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getBrokerTotal() {
        return brokerTotal;
    }

    public BigDecimal getAppSum() {
        return appSum;
    }

    public BigDecimal getDifference() {
        return difference;
    }

    public boolean isMatched() {
        return matched;
    }
}
