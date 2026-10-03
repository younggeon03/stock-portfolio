package com.mystock.portfolio.service.analysis;

import java.time.LocalDateTime;

/**
 * 화면에 내려주는 응답 봉투.
 *
 * 분석 본문(analysis)뿐 아니라 "지금 어떤 상태인지" 를 같이 담는다.
 * 분석이 몇 분 걸리기 때문에 화면이 상태를 보고 로딩 표시나 버튼을 바꿔야 하기 때문이다.
 */
public record CompanyAnalysisResponse(

        /** 종목코드 */
        String symbol,

        /**
         * NONE    아직 분석한 적 없음
         * RUNNING 지금 분석 중
         * OK      분석 완료
         * ERROR   마지막 시도가 실패 (이전 분석이 있으면 analysis 에 그대로 들어있다)
         */
        String status,

        /** 마지막으로 성공한 시각 */
        LocalDateTime analyzedAt,

        /** 설정한 보관 일수를 넘겼는지. 화면에 "오래된 분석" 으로 표시 */
        boolean stale,

        /** 분석한 지 며칠 됐는지 */
        Integer ageDays,

        /** 마지막 시도가 실패했을 때의 이유 */
        String lastError,

        /** 앱이 붙이는 고정 안내문. 모델이 만들지 않는다 */
        String disclaimer,

        /** 어떤 모델이 만든 분석인지. 화면에 표시해서 출처를 분명히 한다 */
        String model,

        Integer inputTokens,
        Integer outputTokens,
        Integer webSearchCount,

        /**
         * 이 분석에 내 평단가·수량·손익이 들어갔나. 화면이 "평단가 기준 분석" 인지 "현재가만 본 분석" 인지 표시한다.
         * 분석이 없으면 null
         */
        Boolean includesPosition,

        /** 분석 본문. 아직 성공한 분석이 없으면 null */
        CompanyAnalysisView analysis
) {

    /** 화면 하단에 항상 붙는 문구 */
    public static final String DISCLAIMER =
            "이 분석은 클로드가 웹 검색으로 조사한 참고자료입니다. "
                    + "수치에는 출처가 붙어 있으니 반드시 원문을 확인하세요. "
                    + "투자 권유가 아니며, 이 앱은 어떤 경우에도 주문을 내지 않습니다.";

    /** 아직 분석한 적 없는 종목 */
    public static CompanyAnalysisResponse none(String symbol) {
        return new CompanyAnalysisResponse(symbol, "NONE", null, false, null, null,
                DISCLAIMER, null, null, null, null, null, null);
    }
}
