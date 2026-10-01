package com.mystock.portfolio.service;

import com.mystock.portfolio.external.toss.TossMarketDataService;
import com.mystock.portfolio.external.toss.dto.TossCandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 일봉 데이터를 가지고 "이 종목이 얼마나 출렁이는가(변동성)" 와 차트를 만들어 준다.
 *
 * ★ 변동성이 왜 중요한가
 * 수익률만 보면 SOXL(+253%)이 제일 좋아 보이지만, 이런 종목은 떨어질 때도 그만큼 떨어진다.
 * 변동성을 같이 보면 "이 수익은 얼마나 위험을 감수하고 얻은 것인가" 를 판단할 수 있다.
 * 4단계에서 목표 비중을 정할 때도 이 숫자가 기준이 된다.
 */
@Service
public class TossAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(TossAnalysisService.class);

    /** 변동성 계산 기간. 60거래일이면 대략 3개월치다 */
    public static final int DEFAULT_DAYS = 60;

    /**
     * 차트에 쓸 봉 개수.
     *
     * 120일선을 그리려면 최소 120개가 필요하고, 선이 의미 있게 보이려면 더 있어야 한다.
     * 토스가 한 번에 주는 최대치가 정확히 200개라 그만큼 받는다.
     */
    public static final int CHART_DAYS = 200;

    /** 그릴 이동평균선. 국내 증권사 기본값이다 (단기·월·분기·반기) */
    private static final int[] MA_PERIODS = {5, 20, 60, 120};

    private final TossMarketDataService marketDataService;

    public TossAnalysisService(TossMarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    /**
     * 여러 종목의 변동성을 한 번에 계산한다.
     *
     * ★ 한 종목이 실패해도 전체를 실패시키지 않는다.
     * 종목 수만큼 시세 API 를 부르기 때문에 중간에 호출 한도(429)에 걸릴 수 있다.
     * 그때 전부 에러로 처리해버리면 화면에 아무것도 안 뜬다.
     * 그래서 종목별로 try-catch 해서 성공한 것만이라도 보여준다.
     */
    public List<TossAnalysisView.Volatility> volatilities(List<String> symbols, int days) {
        List<TossAnalysisView.Volatility> results = new ArrayList<>();

        for (String symbol : symbols) {
            try {
                List<Double> closes = closingPrices(symbol, days);

                if (closes.size() < 2) {
                    results.add(TossAnalysisView.Volatility.failed(symbol, "일봉 데이터가 부족해서 계산할 수 없습니다."));
                    continue;
                }

                results.add(TossAnalysisView.Volatility.of(
                        symbol,
                        StockAnalytics.annualizedVolatilityPercent(closes),
                        StockAnalytics.dailyVolatilityPercent(closes),
                        StockAnalytics.periodReturnPercent(closes),
                        closes.size()));

            } catch (Exception e) {
                log.warn("{} 변동성 계산 실패: {}", symbol, e.getMessage());
                results.add(TossAnalysisView.Volatility.failed(symbol, e.getMessage()));
            }
        }
        return results;
    }

    /**
     * 종목 하나의 캔들차트 데이터 (과거 → 현재 순서).
     *
     * 시가·고가·저가·종가를 모두 담고 이동평균선 4개를 함께 계산한다.
     */
    public TossAnalysisView.Chart chart(String symbol, int days) {
        List<TossCandle> candles = marketDataService.dailyCandlesOldestFirst(symbol, days).stream()
                .filter(candle -> candle.closePrice() != null)
                .toList();

        if (candles.isEmpty()) {
            return new TossAnalysisView.Chart(symbol, null, List.of(), List.of());
        }

        List<TossAnalysisView.Chart.Point> points = candles.stream()
                .map(candle -> new TossAnalysisView.Chart.Point(
                        toDate(candle.timestamp()),
                        // 시가·고가·저가가 비어 있으면 종가로 채운다.
                        // 그래야 캔들이 찌그러지지 않고 점처럼 그려진다.
                        nvl(candle.openPrice(), candle.closePrice()),
                        nvl(candle.highPrice(), candle.closePrice()),
                        nvl(candle.lowPrice(), candle.closePrice()),
                        candle.closePrice(),
                        candle.volume()))
                .toList();

        List<Double> closes = candles.stream()
                .map(candle -> candle.closePrice().doubleValue())
                .toList();

        List<TossAnalysisView.Chart.MovingAverage> movingAverages = new ArrayList<>();
        for (int period : MA_PERIODS) {
            movingAverages.add(buildMovingAverage(points, closes, period));
        }

        return new TossAnalysisView.Chart(symbol, candles.get(0).currency(), points, movingAverages);
    }

    /**
     * 이동평균선 하나를 만든다.
     *
     * 평균을 낼 수 없는 앞부분(null)은 목록에서 빼버린다.
     * 그러면 차트가 값이 있는 지점부터 선을 시작한다.
     * 예를 들어 120일선은 121번째 날부터 그려진다.
     */
    private TossAnalysisView.Chart.MovingAverage buildMovingAverage(
            List<TossAnalysisView.Chart.Point> points, List<Double> closes, int period) {

        List<Double> averages = StockAnalytics.movingAverage(closes, period);
        List<TossAnalysisView.Chart.MovingAverage.Value> values = new ArrayList<>();

        for (int i = 0; i < averages.size(); i++) {
            Double average = averages.get(i);
            if (average == null) {
                continue;
            }
            values.add(new TossAnalysisView.Chart.MovingAverage.Value(
                    points.get(i).date(),
                    BigDecimal.valueOf(average).setScale(4, RoundingMode.HALF_UP)));
        }
        return new TossAnalysisView.Chart.MovingAverage(period, values);
    }

    /** 값이 없으면 대신 쓸 값을 돌려준다 */
    private BigDecimal nvl(BigDecimal value, BigDecimal fallback) {
        return value == null ? fallback : value;
    }

    /** 종가만 뽑아서 double 리스트로 (과거 → 현재 순서) */
    private List<Double> closingPrices(String symbol, int days) {
        return marketDataService.dailyCandlesOldestFirst(symbol, days).stream()
                .map(TossCandle::closePrice)
                .filter(Objects::nonNull)
                .map(BigDecimal::doubleValue)
                .toList();
    }

    /**
     * "2026-09-11T00:00:00-04:00" → "2026-09-11"
     * 차트 가로축에는 날짜만 있으면 충분하다.
     */
    private String toDate(String timestamp) {
        if (timestamp == null) {
            return "";
        }
        return timestamp.length() >= 10 ? timestamp.substring(0, 10) : timestamp;
    }
}
