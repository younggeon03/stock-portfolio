package com.mystock.portfolio.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 13F 보유 한 줄. 같은 종목이 운용역마다 나뉜 원본을 CUSIP·풋콜 단위로 합친 값이다.
 *
 * 풋·콜 옵션은 같은 CUSIP 이라도 따로 둔다. 주식 100만 주와 풋옵션 100만 주는 반대 방향이라
 * 합치면 "많이 들고 있다" 로 잘못 읽힌다.
 */
@Entity
@Table(name = "holding_13f",
        uniqueConstraints = @UniqueConstraint(name = "uk_holding_13f_filing_cusip",
                columnNames = {"accession_no", "cusip", "put_call"}))
public class Holding13F {

    /** 주식(옵션이 아닌 것)일 때 put_call 값 */
    public static final String NOT_OPTION = "";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "accession_no", nullable = false, length = 25)
    private String accessionNo;

    @Column(nullable = false, length = 9)
    private String cusip;

    @Column(name = "issuer_name", nullable = false, length = 200)
    private String issuerName;

    @Column(name = "title_of_class", length = 100)
    private String titleOfClass;

    /** "PUT", "CALL", 주식이면 빈 문자열 */
    @Column(name = "put_call", nullable = false, length = 4)
    private String putCall;

    /** SH(주식 수) 또는 PRN(채권 원금) */
    @Column(name = "share_type", length = 3)
    private String shareType;

    @Column(nullable = false)
    private long shares;

    @Column(name = "value_usd", nullable = false)
    private long valueUsd;

    protected Holding13F() {
        // JPA 기본 생성자
    }

    public Holding13F(String accessionNo, String cusip, String issuerName, String titleOfClass,
                      String putCall, String shareType, long shares, long valueUsd) {
        this.accessionNo = accessionNo;
        this.cusip = cusip;
        this.issuerName = issuerName;
        this.titleOfClass = titleOfClass;
        this.putCall = putCall == null ? NOT_OPTION : putCall;
        this.shareType = shareType;
        this.shares = shares;
        this.valueUsd = valueUsd;
    }

    public Long getId() {
        return id;
    }

    public String getAccessionNo() {
        return accessionNo;
    }

    public String getCusip() {
        return cusip;
    }

    public String getIssuerName() {
        return issuerName;
    }

    public String getTitleOfClass() {
        return titleOfClass;
    }

    public String getPutCall() {
        return putCall;
    }

    public String getShareType() {
        return shareType;
    }

    public long getShares() {
        return shares;
    }

    public long getValueUsd() {
        return valueUsd;
    }
}
