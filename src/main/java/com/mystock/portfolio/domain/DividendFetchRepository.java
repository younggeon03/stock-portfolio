package com.mystock.portfolio.domain;

import org.springframework.data.jpa.repository.JpaRepository;

/** 종목별 마지막 DART 조회 시각 */
public interface DividendFetchRepository extends JpaRepository<DividendFetch, String> {
}
