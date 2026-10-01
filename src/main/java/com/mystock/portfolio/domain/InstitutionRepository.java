package com.mystock.portfolio.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 따라가는 기관 목록 */
public interface InstitutionRepository extends JpaRepository<Institution, Long> {

    List<Institution> findByActiveTrueOrderBySortOrder();
}
