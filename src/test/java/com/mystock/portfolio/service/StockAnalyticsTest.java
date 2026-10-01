package com.mystock.portfolio.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 이동평균 계산 테스트.
 *
 * ★ 왜 이걸 테스트하는가
 * 이평선은 차트에서 가장 눈에 띄는 요소다. 한 칸만 밀려도 사람이 바로 알아채고,
 * 앞부분을 0으로 채우면 선이 바닥에서 솟구치는 이상한 그림이 나온다.
 * 값의 위치와 빈 구간 처리를 테스트로 고정해둔다.
 */
class StockAnalyticsTest {

    @Test
    void 기간만큼_모이기_전까지는_값이_없다() {
        // 5일선이라면 5일치가 모여야 첫 평균이 나온다
        List<Double> result = StockAnalytics.movingAverage(List.of(1.0, 2.0, 3.0, 4.0, 5.0), 5);

        assertThat(result).hasSize(5);
        assertThat(result.subList(0, 4)).containsOnlyNulls();
        assertThat(result.get(4)).isEqualTo(3.0);   // (1+2+3+4+5)/5
    }

    @Test
    void 창이_한_칸씩_밀리며_평균이_계산된다() {
        List<Double> result = StockAnalytics.movingAverage(List.of(1.0, 2.0, 3.0, 4.0, 5.0, 6.0), 3);

        assertThat(result).hasSize(6);
        assertThat(result.get(0)).isNull();
        assertThat(result.get(1)).isNull();
        assertThat(result.get(2)).isEqualTo(2.0);   // (1+2+3)/3
        assertThat(result.get(3)).isEqualTo(3.0);   // (2+3+4)/3
        assertThat(result.get(4)).isEqualTo(4.0);   // (3+4+5)/3
        assertThat(result.get(5)).isEqualTo(5.0);   // (4+5+6)/3
    }

    @Test
    void 길이는_입력과_항상_같다() {
        // 몇 번째 날의 값인지 헷갈리지 않으려면 길이가 맞아야 한다.
        // 화면에서 날짜와 짝지을 때 이 성질에 기댄다.
        List<Double> closes = List.of(10.0, 11.0, 12.0, 13.0, 14.0, 15.0, 16.0);

        assertThat(StockAnalytics.movingAverage(closes, 1)).hasSize(7);
        assertThat(StockAnalytics.movingAverage(closes, 3)).hasSize(7);
        assertThat(StockAnalytics.movingAverage(closes, 7)).hasSize(7);
    }

    @Test
    void 기간이_데이터보다_길면_전부_비어있다() {
        // 120일선인데 봉이 60개뿐인 경우다. 값 없이 조용히 비어야 한다.
        List<Double> result = StockAnalytics.movingAverage(List.of(1.0, 2.0, 3.0), 10);

        assertThat(result).hasSize(3);
        assertThat(result).containsOnlyNulls();
    }

    @Test
    void 기간이_1이면_종가_그대로다() {
        List<Double> closes = List.of(5.0, 7.0, 9.0);
        assertThat(StockAnalytics.movingAverage(closes, 1)).containsExactly(5.0, 7.0, 9.0);
    }

    @Test
    void 빈_목록도_터지지_않는다() {
        assertThat(StockAnalytics.movingAverage(List.of(), 5)).isEmpty();
    }

    @Test
    void 기간이_0_이하면_거부한다() {
        assertThatThrownBy(() -> StockAnalytics.movingAverage(List.of(1.0, 2.0), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 이상");
    }

    @Test
    void 창을_밀면서_계산해도_오차가_쌓이지_않는다() {
        // 합계를 빼고 더하는 방식이라 실수 오차가 누적될 수 있다.
        // 200봉(실제 차트 길이)까지 돌려도 직접 계산한 값과 같아야 한다.
        List<Double> closes = new java.util.ArrayList<>();
        for (int i = 1; i <= 200; i++) {
            closes.add(i * 1.37);
        }

        List<Double> result = StockAnalytics.movingAverage(closes, 20);

        // 마지막 값은 181~200번째의 평균이다
        double expected = 0;
        for (int i = 180; i < 200; i++) {
            expected += closes.get(i);
        }
        expected /= 20;

        assertThat(result.get(199)).isCloseTo(expected, org.assertj.core.data.Offset.offset(0.000001));
    }
}
