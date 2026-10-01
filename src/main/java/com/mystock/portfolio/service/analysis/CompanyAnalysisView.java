package com.mystock.portfolio.service.analysis;

import java.util.List;

/**
 * 클로드가 제출한 분석 결과를 담는 타입.
 *
 * 필드 이름이 company-analysis-schema.json 의 키와 정확히 같아야 한다.
 * 한쪽을 고치면 다른 쪽도 같이 고쳐야 한다.
 *
 * 화면(portfolio.js)은 이 모양을 그대로 그린다.
 */
public record CompanyAnalysisView(

        /** 종목코드 */
        String symbol,

        /** 종목명 */
        String name,

        /** STOCK / ETF / UNKNOWN */
        String instrumentType,

        /** 한 문장 요약 */
        String oneLineSummary,

        /** 지금 더 사도 되는지에 대한 판정. 화면 맨 위에 뜬다 */
        Verdict verdict,

        /** 7개 섹션. 정해진 순서대로 온다 */
        List<Section> sections,

        /** 이 보유 상태에서 감당 중인 위험 */
        List<Risk> risks
) {

    /**
     * 판정.
     *
     * ★ 이건 종목에 대한 의견이 아니라 <b>이 사람의 보유 상태에 대한 의견</b>이다.
     * 같은 SOXL 이라도 자산의 85% 를 넣은 사람과 안 들고 있는 사람에게 같은 답을 주면 안 된다.
     * 증권사 컨센서스가 구조적으로 못 하는 게 이거다. 그쪽은 누가 얼마나 들고 있는지 모른다.
     *
     * 판정에 쓰는 비중·평단가·괴리율은 앱이 계산해서 넘긴 값이다. 모델이 다시 계산하지 않는다.
     */
    public record Verdict(

            /** ADD / HOLD / TRIM / EXIT / UNCLEAR */
            String stance,

            /** 판정을 사람 말로 옮긴 한 줄. 20자 이내 */
            String headline,

            /** 왜 그 판정인지 300자 내외. 비중과 평단가가 반드시 들어간다 */
            String reason,

            /** NEWS / VALUATION / MACRO / INDUSTRY / PRICE / CONCENTRATION */
            List<String> basis
    ) {}

    /** 분석 섹션 하나 */
    public record Section(

            /**
             * 섹션 식별자.
             * FINANCIAL_POSITION / VALUATION_METRICS / GUIDANCE_CONSENSUS
             * / REVENUE_BREAKDOWN / BUSINESS_ANALYSIS / PROFIT_STRUCTURE / POSITION_REVIEW
             */
            String key,

            /** 화면에 보일 한국어 제목 */
            String title,

            /** 이 종목에 이 섹션이 성립하는지. ETF 면 재무 섹션이 false 가 된다 */
            boolean applicable,

            /** 성립하지 않는 이유. 성립하면 빈 문자열 */
            String notApplicableReason,

            /** 서술 본문. 문단은 빈 줄로 나뉘어 있다 */
            String body,

            /** 핵심 포인트 */
            List<String> bullets,

            /** 수치 지표 */
            List<Metric> metrics,

            /** 이 섹션의 근거 출처 */
            List<Source> sources
    ) {
    }

    /** 수치 지표 하나 */
    public record Metric(

            /** 예: PER, ROE, 부채비율 */
            String label,

            /** 이 숫자를 어떻게 읽어야 하는지 */
            String note,

            /**
             * 기간별 값.
             *
             * ★ 왜 값 하나가 아니라 목록인가
             * PER 이 13배라는 사실보다 "지난 분기 12배에서 13배가 됐다" 가 훨씬 많은 걸 말해준다.
             * 값 하나만 보여주면 그 숫자가 좋아지는 중인지 나빠지는 중인지 알 수 없다.
             * 화면에서 분기와 연도를 전환해 본다.
             */
            List<Point> points
    ) {

        /** 한 기간의 값 */
        public record Point(

                /** "2026 2분기", "2025", "2026-09-10" */
                String period,

                /** QUARTER / ANNUAL / POINT */
                String periodType,

                /** 단위를 포함한 값. 못 찾았으면 "모름" */
                String value,

                /** 같은 섹션 sources 의 인덱스. 출처가 없으면 -1 */
                int sourceIndex
        ) {}
    }

    /** 출처 */
    public record Source(
            String title,
            String url,
            String publisher,
            String publishedAt
    ) {
    }

    /** 위험 */
    public record Risk(

            /** 예: 단일 종목 집중 */
            String title,

            /** 왜 위험인지 */
            String detail,

            /** HIGH / MEDIUM / LOW */
            String severity
    ) {
    }
}
