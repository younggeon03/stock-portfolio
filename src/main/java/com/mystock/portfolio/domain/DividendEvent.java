package com.mystock.portfolio.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DART 배당결정 공시 한 건에서 꺼낸 배당.
 *
 * 정정 공시([기재정정])도 따로 한 줄이다. 같은 종목·기준일이면 접수번호가 큰(나중) 것을 쓴다.
 * 원본을 지우지 않는 이유: 공시는 기록이고, 무엇이 언제 바뀌었는지가 남아야 한다.
 */
@Entity
@Table(name = "dividend_event")
public class DividendEvent {

    @Id
    @Column(name = "rcept_no", length = 14)
    private String rceptNo;

    @Column(name = "stock_code", nullable = false, length = 6)
    private String stockCode;

    @Column(name = "corp_name", nullable = false, length = 150)
    private String corpName;

    /** 결산배당 / 분기배당 / 중간배당 */
    @Column(length = 20)
    private String kind;

    /** 현금배당 / 주식배당 / 현물배당 */
    @Column(name = "cash_type", length = 20)
    private String cashType;

    @Column(name = "per_share_common", nullable = false, precision = 15, scale = 2)
    private BigDecimal perShareCommon;

    @Column(name = "per_share_preferred", precision = 15, scale = 2)
    private BigDecimal perSharePreferred;

    @Column(name = "yield_common", precision = 7, scale = 2)
    private BigDecimal yieldCommon;

    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate;

    /** 결산배당은 주주총회 전이면 아직 없다 */
    @Column(name = "pay_date")
    private LocalDate payDate;

    @Column(name = "board_date")
    private LocalDate boardDate;

    /** 공시 접수일 */
    @Column(name = "announced_date", nullable = false)
    private LocalDate announcedDate;

    @Column(nullable = false)
    private boolean correction;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    protected DividendEvent() {
        // JPA 기본 생성자
    }

    public DividendEvent(String rceptNo, String stockCode, String corpName, String kind, String cashType,
                         BigDecimal perShareCommon, BigDecimal perSharePreferred, BigDecimal yieldCommon,
                         LocalDate recordDate, LocalDate payDate, LocalDate boardDate, LocalDate announcedDate,
                         boolean correction) {
        this.rceptNo = rceptNo;
        this.stockCode = stockCode;
        this.corpName = corpName;
        this.kind = kind;
        this.cashType = cashType;
        this.perShareCommon = perShareCommon;
        this.perSharePreferred = perSharePreferred;
        this.yieldCommon = yieldCommon;
        this.recordDate = recordDate;
        this.payDate = payDate;
        this.boardDate = boardDate;
        this.announcedDate = announcedDate;
        this.correction = correction;
        this.fetchedAt = LocalDateTime.now();
    }

    public String getRceptNo() { return rceptNo; }
    public String getStockCode() { return stockCode; }
    public String getCorpName() { return corpName; }
    public String getKind() { return kind; }
    public String getCashType() { return cashType; }
    public BigDecimal getPerShareCommon() { return perShareCommon; }
    public BigDecimal getPerSharePreferred() { return perSharePreferred; }
    public BigDecimal getYieldCommon() { return yieldCommon; }
    public LocalDate getRecordDate() { return recordDate; }
    public LocalDate getPayDate() { return payDate; }
    public LocalDate getBoardDate() { return boardDate; }
    public LocalDate getAnnouncedDate() { return announcedDate; }
    public boolean isCorrection() { return correction; }
    public LocalDateTime getFetchedAt() { return fetchedAt; }
}
