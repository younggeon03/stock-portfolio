package com.mystock.portfolio.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 기관 하나가 한 분기에 낸 13F-HR 한 건.
 *
 * 접수번호(accession number)가 키다. 이미 받은 접수번호면 다시 받지 않아서
 * 매일 돌려도 SEC 호출이 거의 안 늘어난다.
 */
@Entity
@Table(name = "filing_13f")
public class Filing13F {

    /** 예: 0001193125-26-352200 */
    @Id
    @Column(name = "accession_no", length = 25)
    private String accessionNo;

    @Column(nullable = false)
    private long cik;

    /** 어느 분기말 기준 보유인지. 예: 2026-06-30 */
    @Column(name = "report_period", nullable = false)
    private LocalDate reportPeriod;

    /** SEC 에 낸 날. 분기말보다 최대 45일 늦다. 이 날 전에는 아무도 이 보유를 몰랐다 */
    @Column(name = "filed_date", nullable = false)
    private LocalDate filedDate;

    /** 보유 합계(달러). 2023년부터 13F 금액 단위가 천 달러에서 달러로 바뀌었고, 그 뒤 것만 받는다 */
    @Column(name = "total_value_usd", nullable = false)
    private long totalValueUsd;

    @Column(name = "holding_count", nullable = false)
    private int holdingCount;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    protected Filing13F() {
        // JPA 기본 생성자
    }

    public Filing13F(String accessionNo, long cik, LocalDate reportPeriod, LocalDate filedDate,
                     long totalValueUsd, int holdingCount) {
        this.accessionNo = accessionNo;
        this.cik = cik;
        this.reportPeriod = reportPeriod;
        this.filedDate = filedDate;
        this.totalValueUsd = totalValueUsd;
        this.holdingCount = holdingCount;
        this.fetchedAt = LocalDateTime.now();
    }

    public String getAccessionNo() {
        return accessionNo;
    }

    public long getCik() {
        return cik;
    }

    public LocalDate getReportPeriod() {
        return reportPeriod;
    }

    public LocalDate getFiledDate() {
        return filedDate;
    }

    public long getTotalValueUsd() {
        return totalValueUsd;
    }

    public int getHoldingCount() {
        return holdingCount;
    }

    public LocalDateTime getFetchedAt() {
        return fetchedAt;
    }
}
