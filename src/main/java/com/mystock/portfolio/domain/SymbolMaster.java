package com.mystock.portfolio.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 종목 마스터. 토스에서 받아온 전체 종목 목록을 저장해둔다.
 *
 * ★ 왜 저장하는가
 * 스크린샷에서 읽은 "현대차" 라는 글자를 실제 종목코드 005380 으로 바꿔야 한다.
 * 그러려면 종목명과 코드를 맺어주는 표가 필요하다.
 *
 * 토스 문서가 "일 배치로 갱신되는 저변동 데이터이므로 하루 1회 조회 후 로컬 캐싱을 권장" 한다고
 * 명시하고 있다. 마켓당 수천 건이라 매번 받아오면 느리고 호출 한도도 낭비된다.
 *
 * ★ 검색용 이름을 따로 둔다
 * 사람이 "현대 차" 나 "HYUNDAI" 처럼 띄어쓰기나 대소문자를 다르게 쓸 수 있다.
 * 그래서 공백과 대소문자를 없앤 형태를 미리 만들어 저장하고, 검색할 때 그걸로 비교한다.
 */
@Entity
@Table(name = "symbol_master", indexes = {
        @Index(name = "idx_symbol_master_symbol", columnList = "symbol"),
        @Index(name = "idx_symbol_master_search", columnList = "search_name")
})
public class SymbolMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 종목코드. 국내 6자리, 미국 티커 */
    @Column(nullable = false, length = 20)
    private String symbol;

    /** 종목명 (화면 표시용 원본) */
    @Column(nullable = false, length = 150)
    private String name;

    /** 검색용으로 다듬은 이름. 공백 제거 + 소문자 */
    @Column(name = "search_name", nullable = false, length = 150)
    private String searchName;

    /** 상장 시장. KOSPI / NASDAQ 등 */
    @Column(nullable = false, length = 20)
    private String market;

    /** KR 또는 US */
    @Column(name = "market_country", nullable = false, length = 2)
    private String marketCountry;

    /** KRW 또는 USD */
    @Column(nullable = false, length = 3)
    private String currency;

    /** STOCK / ETF 등 */
    @Column(name = "security_type", length = 30)
    private String securityType;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected SymbolMaster() {
        // JPA 기본 생성자
    }

    public SymbolMaster(String symbol, String name, String market, String marketCountry,
                        String currency, String securityType) {
        this.symbol = symbol;
        this.name = name;
        this.searchName = normalize(name);
        this.market = market;
        this.marketCountry = marketCountry;
        this.currency = currency;
        this.securityType = securityType;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 검색용으로 글자를 다듬는다.
     * 공백·점·하이픈을 없애고 소문자로 바꾼다.
     * 이래야 "현대 차" 와 "현대차", "Apple Inc." 와 "appleinc" 가 같은 것으로 취급된다.
     */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("[\\s.\\-_(),]", "").toLowerCase();
    }

    public void refresh(String name, String market, String marketCountry,
                        String currency, String securityType) {
        this.name = name;
        this.searchName = normalize(name);
        this.market = market;
        this.marketCountry = marketCountry;
        this.currency = currency;
        this.securityType = securityType;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getName() {
        return name;
    }

    public String getSearchName() {
        return searchName;
    }

    public String getMarket() {
        return market;
    }

    public String getMarketCountry() {
        return marketCountry;
    }

    public String getCurrency() {
        return currency;
    }

    public String getSecurityType() {
        return securityType;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
