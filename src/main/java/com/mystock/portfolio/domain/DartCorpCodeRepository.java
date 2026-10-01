package com.mystock.portfolio.domain;

import org.springframework.data.jpa.repository.JpaRepository;

/** DART 고유번호 매핑 저장소. 키가 종목코드다 */
public interface DartCorpCodeRepository extends JpaRepository<DartCorpCode, String> {
}
