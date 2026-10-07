package com.mystock.portfolio.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface PortfolioSnapshotItemRepository extends JpaRepository<PortfolioSnapshotItem, Long> {

    List<PortfolioSnapshotItem> findBySnapshotDateOrderByMarketValueKrwDesc(LocalDate date);

    /**
     * 그날 줄을 한 번에 지운다. 다시 찍을 때 새로 쓰기 전에 부른다.
     * 파생 delete(deleteBy…)는 한 줄씩 읽고 지워서, 바로 이어 같은 (날짜, 종목)을 넣으면
     * 지우기가 아직 DB 에 안 나가 유니크 키에 걸릴 수 있다. 쿼리 한 번으로 즉시 지운다
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from PortfolioSnapshotItem i where i.snapshotDate = :date")
    int deleteAllOn(LocalDate date);
}
