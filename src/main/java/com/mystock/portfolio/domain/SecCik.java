package com.mystock.portfolio.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 미국 티커(AVGO) → SEC 회사번호 CIK(1730168) 매핑.
 *
 * EDGAR 재무 API 는 티커를 안 받고 CIK 만 받는다. dart_corp_code 와 같은 이유로 저장해 둔다.
 * 매핑 파일은 800KB 에 약 1만 건이다.
 */
@Entity
@Table(name = "sec_cik")
public class SecCik {

    /** SEC 표기 티커. 클래스 주식은 점이 아니라 하이픈이다 (BRK-B) */
    @Id
    @Column(length = 20)
    private String ticker;

    @Column(nullable = false)
    private long cik;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected SecCik() {
        // JPA 기본 생성자
    }

    public SecCik(String ticker, long cik, String title) {
        this.ticker = ticker;
        this.cik = cik;
        this.title = title;
        this.updatedAt = LocalDateTime.now();
    }

    public String getTicker() {
        return ticker;
    }

    public long getCik() {
        return cik;
    }

    public String getTitle() {
        return title;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
