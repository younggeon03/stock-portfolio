package com.mystock.portfolio.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 변동성·차트 화면에 내려줄 데이터 모음.
 *
 * 두 가지 용도로 쓴다.
 *   Volatility : 표에 붙일 종목별 변동성 한 줄
 *   Chart      : 종목을 클릭했을 때 그릴 캔들차트용 데이터
 */
public final class TossAnalysisView {

    private TossAnalysisView() {
    }

    /**
     * 종목 하나의 변동성 분석 결과.
     *
     * 한 종목이 실패해도(호출 한도 초과 등) 나머지는 보여줘야 하므로,
     * 실패 이유를 error 필드에 담아서 같이 내려준다. 성공하면 error 는 null 이다.
     */
    public record Volatility(

            /** 종목코드 */
            String symbol,

            /**
             * 연환산 변동성(%). 종목끼리 비교할 때 쓰는 대표 숫자.
             * 대략적인 감: 대형 우량주 20~30, 성장주 40~60, 레버리지 ETF 80 이상
             */
            BigDecimal annualizedVolatilityPercent,

            /** 하루 단위 변동성(%). 연환산 전 원본 값 */
            BigDecimal dailyVolatilityPercent,

            /** 조회 기간 동안의 수익률(%). 내 수익률이 아니라 "종목 자체가 그 기간에 얼마나 올랐나" */
            BigDecimal periodReturnPercent,

            /** 계산에 사용한 봉 개수. 너무 적으면 숫자를 믿기 어렵다 */
            int dataPoints,

            /** 실패했을 때의 이유. 성공하면 null */
            String error
    ) {

        /** 계산에 성공한 경우 */
        public static Volatility of(String symbol, double annualized, double daily, double periodReturn, int dataPoints) {
            return new Volatility(
                    symbol,
                    round(annualized),
                    round(daily),
                    round(periodReturn),
                    dataPoints,
                    null);
        }

        /** 실패한 경우. 숫자는 전부 null 로 두고 이유만 담는다 */
        public static Volatility failed(String symbol, String reason) {
            return new Volatility(symbol, null, null, null, 0, reason);
        }

        private static BigDecimal round(double value) {
            return BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP);
        }
    }

    /**
     * 캔들차트용 데이터. 과거 → 현재 순서로 담는다.
     *
     * ★ 예전에는 종가만 담았다
     * 토스는 시가·고가·저가·종가·거래량을 전부 주는데 서버가 종가만 남기고 버리고 있었다.
     * 그래서 선 그래프밖에 못 그렸다. 이제 전부 담아서 음봉·양봉이 있는 캔들차트를 그린다.
     */
    public record Chart(

            String symbol,

            /** KRW 또는 USD. 화면에서 단위를 붙일 때 쓴다 */
            String currency,

            /** 캔들 목록 */
            List<Point> points,

            /** 이동평균선들. 5일·20일·60일·120일 */
            List<MovingAverage> movingAverages
    ) {

        /**
         * 캔들 하나 = 하루.
         *
         * 날짜를 "2026-09-14" 형식 문자열로 두는 이유는 차트 라이브러리가
         * 그 형식을 그대로 받기 때문이다. 화면에서 변환할 필요가 없다.
         */
        public record Point(

                /** "2026-09-14" */
                String date,

                /** 시가. 그날 처음 거래된 가격 */
                BigDecimal open,

                /** 고가 */
                BigDecimal high,

                /** 저가 */
                BigDecimal low,

                /** 종가. 이평선과 변동성 계산에 쓰는 값 */
                BigDecimal close,

                /** 거래량 */
                BigDecimal volume
        ) {
        }

        /**
         * 이동평균선 하나.
         *
         * period 가 5면 5일선이다. 최근 5일 종가의 평균을 이어 그린 선으로,
         * 하루하루의 출렁임을 걷어내고 흐름만 보여준다.
         */
        public record MovingAverage(

                /** 며칠 평균인지. 5 / 20 / 60 / 120 */
                int period,

                /** 선을 이루는 점들 */
                List<Value> values
        ) {

            /**
             * 점 하나.
             *
             * 앞쪽 구간은 평균을 낼 데이터가 모자라서 값이 없다.
             * (120일선은 121번째 날부터 그려진다)
             * 값이 없는 날은 아예 목록에 넣지 않는다. 그러면 차트가 알아서 그 지점부터 선을 시작한다.
             */
            public record Value(String date, BigDecimal value) {
            }
        }
    }
}
