package com.mystock.portfolio.domain;

import org.springframework.data.jpa.repository.JpaRepository;

/** 공시 재무 저장소. 키가 종목코드(005930, AVGO) */
public interface StoredFinancialsRepository extends JpaRepository<StoredFinancials, String> {
}
