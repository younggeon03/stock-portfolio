package com.mystock.portfolio.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/** 13F 보유 줄 */
public interface Holding13FRepository extends JpaRepository<Holding13F, Long> {

    List<Holding13F> findByAccessionNoOrderByValueUsdDesc(String accessionNo);

    void deleteByAccessionNo(String accessionNo);

    /** 아직 티커를 찾아보지 않은 CUSIP. 주식만 (옵션은 같은 CUSIP 이라 따로 물을 필요가 없다) */
    @Query("select distinct h.cusip from Holding13F h "
            + "where not exists (select 1 from CusipTicker t where t.cusip = h.cusip)")
    List<String> findUnresolvedCusips();
}
