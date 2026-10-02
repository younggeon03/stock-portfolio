package com.mystock.portfolio.domain;

import org.springframework.data.jpa.repository.JpaRepository;

/** DART 고유번호 매핑 저장소. 키가 종목코드다 */
public interface DartCorpCodeRepository extends JpaRepository<DartCorpCode, String> {

    /** 회사 이름이 정확히 같은 상장사. 배당 캘린더에서 "삼성전자" 처럼 이름으로 찾을 때 */
    java.util.Optional<DartCorpCode> findFirstByCorpName(String corpName);
}
