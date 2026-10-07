package com.mystock.portfolio.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

/** 하루 한 줄 스냅샷. 키가 날짜 */
public interface PortfolioSnapshotRepository extends JpaRepository<PortfolioSnapshot, LocalDate> {

    /** 그래프용. 이 날짜부터 오래된 순 */
    List<PortfolioSnapshot> findBySnapshotDateGreaterThanEqualOrderBySnapshotDate(LocalDate from);
}
