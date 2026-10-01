package com.mystock.portfolio.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 사람이 직접 입력한 보유 종목.
 *
 * ★ 왜 필요한가
 * 토스·나무 API 로는 **키를 발급받은 본인의 계좌만** 조회된다.
 * 남의 계좌를 동의받아 보는 기능이 증권사 API 에 아예 없다.
 * 그래서 다른 사람이 이 앱을 쓰려면 보유종목을 직접 알려주는 수밖에 없다.
 *
 * 대신 나머지는 전부 앱이 채워준다. 시세·일봉·종목정보·환율은 계좌와 무관하므로
 * 앱의 키 하나로 어떤 종목이든 조회된다. 그래서 직접 입력한 종목도
 * 자동 연동된 종목과 똑같이 비중·변동성·차트·기업분석이 나온다.
 *
 * ★ ownerKey 로 사람을 구분한다
 * 로그인을 만들지 않고, 브라우저가 처음 접속할 때 만든 임의의 값을 쓴다.
 * 회원가입 없이 각자 자기 포트폴리오를 갖게 하는 가장 간단한 방법이다.
 */
@Entity
@Table(name = "manual_holding",
        uniqueConstraints = @UniqueConstraint(name = "uk_manual_holding_owner_symbol",
                columnNames = {"owner_key", "symbol"}))
public class ManualHolding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 누구의 보유종목인지. 브라우저가 만든 식별자 */
    @Column(name = "owner_key", nullable = false, length = 64)
    private String ownerKey;

    /** 종목코드. 국내 6자리(005930), 미국 티커(NVDA) */
    @Column(nullable = false, length = 20)
    private String symbol;

    /** 종목명 */
    @Column(nullable = false, length = 100)
    private String name;

    /** KR 또는 US */
    @Column(name = "market_country", nullable = false, length = 2)
    private String marketCountry;

    /** KRW 또는 USD */
    @Column(nullable = false, length = 3)
    private String currency;

    /** 보유 수량. 소수점 매수를 지원해야 하므로 소수 자리를 넉넉히 둔다 */
    @Column(nullable = false, precision = 20, scale = 6)
    private BigDecimal quantity;

    /** 매수 평균가 (거래 통화 기준) */
    @Column(name = "average_purchase_price", nullable = false, precision = 20, scale = 4)
    private BigDecimal averagePurchasePrice;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected ManualHolding() {
        // JPA 기본 생성자
    }

    public ManualHolding(String ownerKey, String symbol, String name, String marketCountry,
                         String currency, BigDecimal quantity, BigDecimal averagePurchasePrice) {
        this.ownerKey = ownerKey;
        this.symbol = symbol;
        this.name = name;
        this.marketCountry = marketCountry;
        this.currency = currency;
        this.quantity = quantity;
        this.averagePurchasePrice = averagePurchasePrice;
        this.updatedAt = LocalDateTime.now();
    }

    /** 수량과 평단가를 새 값으로 바꾼다 */
    public void update(String name, String marketCountry, String currency,
                       BigDecimal quantity, BigDecimal averagePurchasePrice) {
        this.name = name;
        this.marketCountry = marketCountry;
        this.currency = currency;
        this.quantity = quantity;
        this.averagePurchasePrice = averagePurchasePrice;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getOwnerKey() {
        return ownerKey;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getName() {
        return name;
    }

    public String getMarketCountry() {
        return marketCountry;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getAveragePurchasePrice() {
        return averagePurchasePrice;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
