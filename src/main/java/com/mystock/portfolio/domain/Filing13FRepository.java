package com.mystock.portfolio.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 받아 둔 13F 제출 목록 */
public interface Filing13FRepository extends JpaRepository<Filing13F, String> {

    /** 최근 분기부터 */
    List<Filing13F> findByCikOrderByReportPeriodDesc(long cik);

    /** 피드용: 보유가 있는 제출을 공개일 최신부터 20건 */
    List<Filing13F> findTop20ByHoldingCountGreaterThanOrderByFiledDateDescReportPeriodDesc(int holdingCount);
}
