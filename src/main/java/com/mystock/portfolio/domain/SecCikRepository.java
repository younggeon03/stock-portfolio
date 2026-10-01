package com.mystock.portfolio.domain;

import org.springframework.data.jpa.repository.JpaRepository;

/** SEC CIK 매핑 저장소. 키가 티커다 */
public interface SecCikRepository extends JpaRepository<SecCik, String> {
}
