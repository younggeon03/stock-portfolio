package com.mystock.portfolio.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

/** 배당결정 공시에서 꺼낸 배당 */
public interface DividendEventRepository extends JpaRepository<DividendEvent, String> {

    List<DividendEvent> findByStockCodeAndRecordDateGreaterThanEqualOrderByRecordDateDesc(String stockCode, LocalDate from);
}
