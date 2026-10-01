package com.mystock.portfolio.service;

import java.util.ArrayList;
import java.util.List;

/** 캔들(종가) 데이터로부터 변동성을 계산하는 유틸리티 */
final class StockAnalytics {

    /**
     * 1년 거래일 수. 주말과 공휴일을 빼면 1년에 대략 252일 장이 열린다.
     * 하루치 변동성을 1년치로 환산할 때 √252 를 곱한다.
     *
     * 왜 제곱근이냐면, 변동성은 시간에 비례해서 커지는 게 아니라 시간의 제곱근에 비례해서 커지기 때문이다.
     * (하루 1% 출렁이는 종목이 1년이면 252%가 아니라 약 15.9% 출렁인다)
     */
    private static final double TRADING_DAYS_PER_YEAR = 252.0;

    private StockAnalytics() {
    }

    /**
     * 연환산 변동성(%).
     * 보통 이 값으로 종목을 비교한다. 대형 우량주는 20~30%, 성장주/레버리지 ETF 는 50~100% 이상 나온다.
     */
    static double annualizedVolatilityPercent(List<Double> closesAsc) {
        return dailyVolatilityPercent(closesAsc) * Math.sqrt(TRADING_DAYS_PER_YEAR);
    }

    /**
     * 단순이동평균(SMA)을 구한다.
     *
     * i번째 값은 "그 날까지 최근 period일 종가의 평균"이다.
     * 하루하루의 출렁임을 걷어내고 흐름만 남기는 게 목적이다.
     *
     * ★ 앞쪽은 값이 없다
     * 5일선이라면 5일치가 모여야 첫 평균이 나온다. 그 전 4일은 평균을 낼 수 없다.
     * 0 을 채우면 차트가 바닥에서 솟구치는 이상한 선을 그리므로 **null 을 넣는다.**
     * 길이는 입력과 똑같이 맞춰서, 몇 번째 날의 값인지 헷갈리지 않게 한다.
     *
     * @param closesAsc 과거 → 현재 순서의 종가
     * @param period    며칠 평균인지 (5, 20, 60, 120)
     * @return 입력과 같은 길이. 평균을 낼 수 없는 앞부분은 null
     */
    static List<Double> movingAverage(List<Double> closesAsc, int period) {
        List<Double> result = new ArrayList<>(closesAsc.size());

        if (period <= 0) {
            throw new IllegalArgumentException("이동평균 기간은 1 이상이어야 합니다: " + period);
        }

        // 합계를 매번 다시 더하지 않고, 창을 한 칸씩 밀면서 빠진 값을 빼고 새 값을 더한다.
        // 200개 × 4개 선이라 큰 차이는 없지만 계산 방식이 더 명확하다.
        double windowSum = 0;

        for (int i = 0; i < closesAsc.size(); i++) {
            windowSum += closesAsc.get(i);

            if (i >= period) {
                windowSum -= closesAsc.get(i - period);
            }

            if (i < period - 1) {
                result.add(null);          // 아직 period일치가 모이지 않았다
            } else {
                result.add(windowSum / period);
            }
        }
        return result;
    }

    /**
     * 기간 수익률(%). 첫 종가 대비 마지막 종가가 얼마나 올랐는지.
     * 예: 60일 전 100원 → 지금 130원 이면 +30%
     */
    static double periodReturnPercent(List<Double> closesAsc) {
        if (closesAsc.size() < 2) {
            return 0.0;
        }
        double first = closesAsc.get(0);
        double last = closesAsc.get(closesAsc.size() - 1);
        if (first == 0) {
            return 0.0;
        }
        return (last - first) / first * 100.0;
    }

    /**
     * 과거->현재 순서로 정렬된 종가 리스트로부터 일간 수익률의 표준편차(%)를 계산한다.
     * 값이 클수록 하루하루 가격이 크게 출렁였다는 뜻이다.
     * 데이터가 2개 미만이면 계산이 불가능하므로 0을 반환한다.
     */
    static double dailyVolatilityPercent(List<Double> closesAsc) {
        if (closesAsc.size() < 2) {
            return 0.0;
        }

        double[] dailyReturns = new double[closesAsc.size() - 1];
        for (int i = 1; i < closesAsc.size(); i++) {
            double prev = closesAsc.get(i - 1);
            double curr = closesAsc.get(i);
            dailyReturns[i - 1] = (curr - prev) / prev;
        }

        double mean = 0;
        for (double r : dailyReturns) {
            mean += r;
        }
        mean /= dailyReturns.length;

        double variance = 0;
        for (double r : dailyReturns) {
            variance += Math.pow(r - mean, 2);
        }
        // 표본 표준편차 (n-1). 수익률이 1개뿐이면 분산을 정의할 수 없으므로 0으로 처리.
        variance = dailyReturns.length > 1 ? variance / (dailyReturns.length - 1) : 0;

        return Math.sqrt(variance) * 100.0;
    }
}
