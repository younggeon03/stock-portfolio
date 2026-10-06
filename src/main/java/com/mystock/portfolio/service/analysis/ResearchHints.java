package com.mystock.portfolio.service.analysis;

import java.util.List;

/**
 * 앱이 이미 가진(또는 무료로 받는) 자료. 프롬프트에 사실로 넣고, 그 주제는 웹 검색하지 말라고 한다.
 *
 * ★ 토큰 절감
 * 웹 검색 한 번은 검색 요금에 더해 결과 글(수천 토큰)이 입력으로 들어오고, 이어 부를 때마다 다시 실린다.
 * 뉴스 헤드라인(구글 뉴스 RSS, 무료)과 기관 보유(13F, DB)를 미리 넣으면 그만큼 검색을 덜 한다.
 *
 * @param headlines        최근 뉴스 제목 몇 줄. "제목 (날짜)". 없으면 빈 목록
 * @param institutionLines 미국 종목의 큰 기관 13F 보유·변화 요약 줄. 국내 종목·자료 없음이면 빈 목록
 */
public record ResearchHints(List<String> headlines, List<String> institutionLines) {

    public static final ResearchHints NONE = new ResearchHints(List.of(), List.of());

    public boolean hasHeadlines() {
        return headlines != null && !headlines.isEmpty();
    }

    public boolean hasInstitutions() {
        return institutionLines != null && !institutionLines.isEmpty();
    }
}
