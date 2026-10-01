package com.mystock.portfolio.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 종목코드(005930) → DART 고유번호(00126380) 매핑.
 *
 * ★ 왜 따로 저장하는가
 * DART 재무 API 는 종목코드를 안 받고 고유번호만 받는다. 매핑은 corpCode.xml 한 파일로만 주는데
 * 압축해서 3.6MB, 풀면 30MB 에 비상장사까지 10만 건이 넘는다.
 * 분석할 때마다 받으면 느리고 낭비라서 상장사만 골라 DB 에 넣어둔다. symbol_master 와 같은 방식이다.
 */
@Entity
@Table(name = "dart_corp_code")
public class DartCorpCode {

    /** 한국거래소 종목코드 6자리 */
    @Id
    @Column(name = "stock_code", length = 6)
    private String stockCode;

    /** DART 고유번호 8자리 */
    @Column(name = "corp_code", nullable = false, length = 8)
    private String corpCode;

    @Column(name = "corp_name", nullable = false, length = 150)
    private String corpName;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected DartCorpCode() {
        // JPA 기본 생성자
    }

    public DartCorpCode(String stockCode, String corpCode, String corpName) {
        this.stockCode = stockCode;
        this.corpCode = corpCode;
        this.corpName = corpName;
        this.updatedAt = LocalDateTime.now();
    }

    public String getStockCode() {
        return stockCode;
    }

    public String getCorpCode() {
        return corpCode;
    }

    public String getCorpName() {
        return corpName;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
