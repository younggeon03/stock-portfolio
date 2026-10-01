package com.mystock.portfolio.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * CUSIP(증권 고유번호 9자리) → 티커.
 *
 * 13F 는 티커를 주지 않는다. OpenFIGI 로 바꾸는데 키 없이는 분당 25번이라
 * 한 번 찾은 결과는 여기 남겨 두고 다시 묻지 않는다. 못 찾은 것도 ticker=null 로 남긴다.
 * 안 남기면 채권·워런트처럼 원래 티커가 없는 것을 매일 다시 묻는다.
 */
@Entity
@Table(name = "cusip_ticker")
public class CusipTicker {

    @Id
    @Column(length = 9)
    private String cusip;

    @Column(length = 20)
    private String ticker;

    @Column(name = "figi_name", length = 200)
    private String figiName;

    @Column(name = "security_type", length = 50)
    private String securityType;

    @Column(name = "resolved_at", nullable = false)
    private LocalDateTime resolvedAt;

    protected CusipTicker() {
        // JPA 기본 생성자
    }

    public CusipTicker(String cusip, String ticker, String figiName, String securityType) {
        this.cusip = cusip;
        this.ticker = ticker;
        this.figiName = figiName;
        this.securityType = securityType;
        this.resolvedAt = LocalDateTime.now();
    }

    public String getCusip() {
        return cusip;
    }

    public String getTicker() {
        return ticker;
    }

    public String getFigiName() {
        return figiName;
    }

    public String getSecurityType() {
        return securityType;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }
}
