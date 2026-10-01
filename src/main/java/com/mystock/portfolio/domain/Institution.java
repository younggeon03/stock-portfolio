package com.mystock.portfolio.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 보유 종목을 따라가는 기관 투자자. SEC 의 CIK 로 구분한다.
 *
 * 목록은 마이그레이션(V2)으로 넣는다. 화면에서 추가하는 기능은 일부러 없다.
 * 아무 CIK 나 넣게 하면 수천 종목짜리 기관 하나로 티커 변환 한도를 다 써버린다.
 */
@Entity
@Table(name = "institution")
public class Institution {

    @Id
    private long cik;

    @Column(nullable = false, length = 150)
    private String name;

    /** 화면에 보일 한국어 이름 */
    @Column(name = "name_ko", nullable = false, length = 100)
    private String nameKo;

    /** 운용 책임자. 기관 이름보다 사람 이름으로 더 알려진 곳이 많다 */
    @Column(length = 100)
    private String manager;

    @Column(length = 200)
    private String note;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean active;

    protected Institution() {
        // JPA 기본 생성자
    }

    public long getCik() {
        return cik;
    }

    public String getName() {
        return name;
    }

    public String getNameKo() {
        return nameKo;
    }

    public String getManager() {
        return manager;
    }

    public String getNote() {
        return note;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isActive() {
        return active;
    }
}
