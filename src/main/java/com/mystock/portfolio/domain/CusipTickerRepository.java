package com.mystock.portfolio.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** CUSIP → 티커 캐시. 키가 CUSIP 이다 */
public interface CusipTickerRepository extends JpaRepository<CusipTicker, String> {

    /** 티커 하나에 CUSIP 이 여럿일 수 있다 (주식 종류가 다르거나 CUSIP 이 바뀐 경우) */
    List<CusipTicker> findByTicker(String ticker);
}
