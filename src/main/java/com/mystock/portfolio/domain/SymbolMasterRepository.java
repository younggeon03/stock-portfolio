package com.mystock.portfolio.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 종목 마스터 저장소 */
public interface SymbolMasterRepository extends JpaRepository<SymbolMaster, Long> {

    Optional<SymbolMaster> findBySymbol(String symbol);

    /** 다듬은 이름이 정확히 일치하는 종목들 (동명이인이 있을 수 있어 목록으로) */
    List<SymbolMaster> findBySearchName(String searchName);

    /** 이름에 특정 글자가 들어간 종목들 */
    List<SymbolMaster> findBySearchNameContaining(String part);

    long count();
}
