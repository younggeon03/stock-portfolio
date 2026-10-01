package com.mystock.portfolio.domain;

import org.springframework.data.jpa.repository.JpaRepository;

/** CUSIP → 티커 캐시. 키가 CUSIP 이다 */
public interface CusipTickerRepository extends JpaRepository<CusipTicker, String> {
}
