package com.mystock.portfolio.service.analysis;

import java.util.ArrayList;
import java.util.List;

/**
 * "판단만 새로" 의 제출 결과. company-reassess-schema.json 과 필드 이름이 같아야 한다.
 *
 * ★ 조사와 판단을 나눈 이유 (토큰 절감)
 * 전체 분석 값의 대부분은 웹 검색 결과와 일곱 섹션을 쓰는 출력이다. 그런데 며칠 사이에 바뀌는 건
 * 현재가와 내 보유 상태뿐이고, 회사의 재무·사업·컨센서스 조사는 그대로다.
 * 그래서 보관 일수 안에서는 조사 섹션을 그대로 두고 판정·요약·내 위치·위험만 검색 없이 다시 쓴다.
 *
 * @param positionReview POSITION_REVIEW 섹션의 새 본문
 */
record Reassessment(
        String oneLineSummary,
        CompanyAnalysisView.Verdict verdict,
        PositionReview positionReview,
        List<CompanyAnalysisView.Risk> risks
) {

    record PositionReview(boolean applicable, String notApplicableReason, String body, List<String> bullets) {
    }

    /**
     * 저장된 분석에 새 판단을 덮어쓴 사본. 조사 섹션(POSITION_REVIEW 밖)은 한 글자도 바꾸지 않는다.
     * 모델이 칸을 비우면 옛 값을 남긴다. 반쯤 빈 판단으로 멀쩡한 분석을 덮으면 안 된다
     */
    CompanyAnalysisView mergeInto(CompanyAnalysisView stored) {
        List<CompanyAnalysisView.Section> sections = new ArrayList<>();
        for (CompanyAnalysisView.Section s : stored.sections()) {
            if ("POSITION_REVIEW".equals(s.key()) && positionReview != null) {
                sections.add(new CompanyAnalysisView.Section(s.key(), s.title(),
                        positionReview.applicable(),
                        nvl(positionReview.notApplicableReason()),
                        nvl(positionReview.body()),
                        positionReview.bullets() == null ? List.of() : positionReview.bullets(),
                        List.of(), List.of()));
            } else {
                sections.add(s);
            }
        }
        return new CompanyAnalysisView(stored.symbol(), stored.name(), stored.instrumentType(),
                oneLineSummary == null || oneLineSummary.isBlank() ? stored.oneLineSummary() : oneLineSummary,
                verdict == null ? stored.verdict() : verdict,
                sections,
                risks == null || risks.isEmpty() ? stored.risks() : risks);
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }
}
