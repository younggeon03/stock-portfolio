package com.mystock.portfolio.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 종목마다 DART 공시 목록을 마지막으로 본 때.
 * 공개 화면에서 같은 종목을 여러 사람이 찾아도 DART 는 하루에 한 번만 부르게 한다 (하루 2만 건 한도).
 */
@Entity
@Table(name = "dividend_fetch")
public class DividendFetch {

    @Id
    @Column(name = "stock_code", length = 6)
    private String stockCode;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    protected DividendFetch() {
        // JPA 기본 생성자
    }

    public DividendFetch(String stockCode) {
        this.stockCode = stockCode;
        this.fetchedAt = LocalDateTime.now();
    }

    public String getStockCode() {
        return stockCode;
    }

    public LocalDateTime getFetchedAt() {
        return fetchedAt;
    }
}
