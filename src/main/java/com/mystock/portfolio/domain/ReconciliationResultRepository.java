package com.mystock.portfolio.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface ReconciliationResultRepository extends JpaRepository<ReconciliationResult, Long> {

    List<ReconciliationResult> findBySnapshotDateOrderByBrokerAscScopeAsc(LocalDate date);

    /** 다시 찍을 때 그날 결과를 먼저 지운다. 이유는 PortfolioSnapshotItemRepository.deleteAllOn 과 같다 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ReconciliationResult r where r.snapshotDate = :date")
    int deleteAllOn(LocalDate date);
}
