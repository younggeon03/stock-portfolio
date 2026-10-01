package com.mystock.portfolio.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 기업분석 캐시 저장소.
 *
 * 메서드 이름만 규칙에 맞게 선언하면 스프링이 쿼리를 자동으로 만들어준다.
 */
public interface CompanyAnalysisRepository extends JpaRepository<CompanyAnalysis, Long> {

    /** 종목 하나의 분석 */
    Optional<CompanyAnalysis> findBySymbol(String symbol);

    /** 여러 종목의 분석을 한 번에. 표의 "기업분석" 칸을 채울 때 쓴다 */
    List<CompanyAnalysis> findBySymbolIn(Collection<String> symbols);
}
