package com.mystock.portfolio.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 종목 하나의 공시 재무(DART·SEC 에서 받아 앱이 계산한 값)를 주가 없이 저장한다.
 *
 * ★ 왜 DB 인가
 * 공시 재무는 분기에 한 번 바뀐다. 예전에는 메모리 캐시(6시간)만 있어서 재시작하면 다시 받았고,
 * 서버가 여러 대면 서버마다 따로 받았다. SEC 는 초당 10건, DART 는 하루 2만 건 한도도 있다.
 * PER·PBR 처럼 주가로 매일 바뀌는 값은 저장하지 않고 읽을 때 계산한다(CompanyFinancials.withPrice).
 * 결산일 종가로 잰 과거 PER·PBR(history)은 과거 값이라 바뀌지 않으므로 같이 저장한다.
 *
 * 내용은 CompanyFinancials 를 JSON 으로 통째로 넣는다. 재무 항목이 공시처(DART·SEC)마다 다르고
 * 앞으로 늘 수 있어서, 열로 쪼개면 항목을 더할 때마다 마이그레이션이 필요하다.
 */
@Entity
@Table(name = "stored_financials")
public class StoredFinancials {

    @Id
    @Column(length = 20)
    private String symbol;

    /** KR 또는 US */
    @Column(name = "market_country", nullable = false, length = 2)
    private String marketCountry;

    @Column(name = "financials_json", nullable = false, columnDefinition = "LONGTEXT")
    private String financialsJson;

    /** 과거 PER·PBR 을 붙였나. 토스가 막혀 못 붙였으면 다음에 읽을 때 다시 시도한다 */
    @Column(name = "has_history", nullable = false)
    private boolean hasHistory;

    /** 공시처에서 마지막으로 받은 때. 하루가 지나면 다시 받는다 */
    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    protected StoredFinancials() {
    }

    public StoredFinancials(String symbol, String marketCountry) {
        this.symbol = symbol;
        this.marketCountry = marketCountry;
    }

    public void update(String financialsJson, boolean hasHistory, LocalDateTime fetchedAt) {
        this.financialsJson = financialsJson;
        this.hasHistory = hasHistory;
        this.fetchedAt = fetchedAt;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getMarketCountry() {
        return marketCountry;
    }

    public String getFinancialsJson() {
        return financialsJson;
    }

    public boolean isHasHistory() {
        return hasHistory;
    }

    public LocalDateTime getFetchedAt() {
        return fetchedAt;
    }
}
