package com.mystock.portfolio.service.analysis;

import com.mystock.portfolio.external.filing.CompanyFinancials;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 앱이 "확실히 아는" 사실 묶음.
 *
 * ★ 이 record 의 존재 이유
 * LLM 에게 숫자를 맡기면 틀린다. 보유수량이나 평단가를 지어내면 분석 전체가 쓸모없어진다.
 * 그래서 증권사 API 와 시세 API 에서 직접 가져온 값, 그리고 그걸로 우리가 계산한 값을
 * 한 곳에 모아서 "이건 사실이니 그대로 써라" 라고 프롬프트에 박아 넣는다.
 *
 * 나눗셈(수익률·괴리율·비중)도 우리가 미리 해서 넘긴다.
 * 모델에게 산수를 시키지 않는 것이 환각을 막는 가장 값싼 방법이다.
 */
public record CompanyAnalysisFacts(

        /** 종목코드 */
        String symbol,

        /** 종목명 (한글) */
        String name,

        /** 영문 종목명. 해외 자료를 검색할 때 이 이름이 잘 맞는다 */
        String englishName,

        /** KR 또는 US */
        String marketCountry,

        /** 상장 시장. KOSPI / NASDAQ 등 */
        String market,

        /** 거래 통화. KRW 또는 USD */
        String currency,

        /** 종목 유형. STOCK / ETF / ETN 등 (토스가 알려준 확정값) */
        String securityType,

        /** 레버리지 배수. 일반 주식은 null */
        BigDecimal leverageFactor,

        /** 발행주식수 */
        BigDecimal sharesOutstanding,

        // ── 내 보유 현황 ────────────────────────────────

        /**
         * 내가 가진 종목인가.
         * false 면 아래 보유 값(수량·평단가·손익·비중·보유처)은 전부 null 이고, 현재가만으로 분석한다.
         * 이 값이 그대로 저장돼 "평단가가 들어간 분석인가" 를 가른다. 들어간 분석은 공개 화면에 안 나간다
         */
        boolean held,

        /** 보유 수량 (증권사 합산) */
        BigDecimal quantity,

        /** 현재가 (거래 통화 기준) */
        BigDecimal lastPrice,

        /** 내 평단가 (거래 통화 기준, 수량 가중평균) */
        BigDecimal averagePurchasePrice,

        /** 평단가 대비 현재가 괴리율(%). 양수면 평단가보다 올라 있다 */
        BigDecimal priceGapPercent,

        /** 평가금액 (원화) */
        BigDecimal marketValueKrw,

        /** 매입금액 (원화) */
        BigDecimal purchaseKrw,

        /** 평가손익 (원화) */
        BigDecimal profitLossKrw,

        /** 수익률(%) */
        BigDecimal profitRatePercent,

        /** 전체 자산에서 이 종목이 차지하는 비중(%) */
        BigDecimal weightPercent,

        /** 전체 자산 (원화) */
        BigDecimal totalValueKrw,

        /** 보유처. 예: "나무증권 + 토스증권" */
        String brokers,

        // ── 시세 통계 (최근 60거래일) ────────────────────

        /** 연환산 변동성(%). 못 구했으면 null */
        BigDecimal annualizedVolatilityPercent,

        /** 일간 변동성(%) */
        BigDecimal dailyVolatilityPercent,

        /** 기간 수익률(%) */
        BigDecimal periodReturnPercent,

        /** 계산에 쓴 봉 개수 */
        Integer dataPoints,

        /** 기간 최고 종가 */
        BigDecimal periodHigh,

        /** 기간 최저 종가 */
        BigDecimal periodLow,

        // ── 위험 신호 ───────────────────────────────────

        /**
         * 앱이 확정 데이터로 판정한 위험 신호.
         * 예: "전체 자산의 81.47% 를 이 한 종목이 차지한다", "3배 레버리지 상품이다"
         *
         * 이걸 LLM 판단에 맡기지 않고 우리가 먼저 세워서 넘긴다.
         * 놓치면 안 되는 경고이기 때문이다.
         */
        List<String> riskFlags,

        // ── 공시 재무 ───────────────────────────────────

        /**
         * DART 공시 재무와 앱이 계산한 비율. 국내 개별 기업만 있고, 미국 종목·ETF·키가 없을 때는 null.
         * null 이면 예전처럼 클로드가 웹에서 재무를 찾는다.
         */
        CompanyFinancials financials,

        /** 이 사실들을 조회한 시각 */
        LocalDateTime asOf
) {
}
