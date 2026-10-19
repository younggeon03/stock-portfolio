package com.mystock.portfolio.service.tax;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 토스 체결 내역으로 해외주식 실현손익을 계산하는 규칙. 숫자는 데모 값이고 기댓값은 손으로 계산했다.
 */
class FifoGainCalculatorTest {

    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate TO = LocalDate.of(2026, 12, 31);

    /** 결제일별 기준환율(데모) */
    private static final Function<LocalDate, BigDecimal> RATE = d -> Map.of(
            LocalDate.of(2025, 12, 10), new BigDecimal("1300"),
            LocalDate.of(2025, 12, 20), new BigDecimal("1320"),
            LocalDate.of(2026, 1, 10), new BigDecimal("1400"),
            LocalDate.of(2026, 3, 3), new BigDecimal("1450")).get(d);

    @Test
    void 먼저_산_것부터_팔고_각각_결제일_환율로_원화를_잡는다() {
        var fills = List.of(
                buy("AAA", "10", "1000", "1", "2025-12-10", "2025-12-08T10:00"),
                buy("AAA", "10", "1200", "1", "2026-01-10", "2026-01-08T10:00"),
                sell("AAA", "15", "1950", "2", "2026-03-03", "2026-03-01T10:00"));

        var g = FifoGainCalculator.calculate(fills, FROM, TO, RATE);

        // 취득가: 첫 묶음 전부 (1000+1)×1300 = 1,301,300 + 둘째 묶음 절반 (1201/2)×1400 = 840,700 → 2,142,000
        // 양도가: (1950-2)×1450 = 2,824,600 → 손익 682,600
        assertThat(g.realizedKrw()).isEqualByComparingTo("682600");
        assertThat(g.sellAmountKrw()).isEqualByComparingTo("2824600");
        assertThat(g.notes()).isEmpty();
    }

    @Test
    void 작년에_판_것도_매수_묶음을_소진해서_올해_매도는_뒤_묶음과_맞춘다() {
        var fills = List.of(
                buy("AAA", "10", "1000", "0", "2025-12-10", "2025-12-08T10:00"),
                sell("AAA", "10", "1100", "0", "2025-12-20", "2025-12-18T10:00"),
                buy("AAA", "10", "1200", "0", "2026-01-10", "2026-01-08T10:00"),
                sell("AAA", "10", "1300", "0", "2026-03-03", "2026-03-01T10:00"));

        var g = FifoGainCalculator.calculate(fills, FROM, TO, RATE);

        // 올해 매도만 센다: 1300×1450 - 1200×1400 = 1,885,000 - 1,680,000 = 205,000
        assertThat(g.realizedKrw()).isEqualByComparingTo("205000");
        assertThat(g.sales()).hasSize(1);
    }

    @Test
    void 매수_기록이_없는_수량은_지어내지_않고_빼고_알린다() {
        var fills = List.of(
                buy("AAA", "2", "200", "0", "2026-01-10", "2026-01-08T10:00"),
                sell("AAA", "5", "600", "0", "2026-03-03", "2026-03-01T10:00"));

        var g = FifoGainCalculator.calculate(fills, FROM, TO, RATE);

        // 맞춘 2주만: 양도가 600×2/5=240 → 240×1450 = 348,000, 취득가 200×1400 = 280,000 → 68,000
        assertThat(g.realizedKrw()).isEqualByComparingTo("68000");
        assertThat(g.notes()).singleElement().asString().contains("3주는 매수 기록을 못 찾아");
    }

    @Test
    void 종목끼리_섞이지_않는다() {
        var fills = List.of(
                buy("AAA", "1", "100", "0", "2026-01-10", "2026-01-08T10:00"),
                sell("BBB", "1", "100", "0", "2026-03-03", "2026-03-01T10:00"));

        var g = FifoGainCalculator.calculate(fills, FROM, TO, RATE);

        assertThat(g.realizedKrw()).isEqualByComparingTo("0");
        assertThat(g.sales()).isEmpty();
        assertThat(g.notes()).hasSize(1);
    }

    private static FifoGainCalculator.Fill buy(String s, String q, String amt, String fee, String settle, String at) {
        return new FifoGainCalculator.Fill(s, true, new BigDecimal(q), new BigDecimal(amt), new BigDecimal(fee),
                BigDecimal.ZERO, LocalDate.parse(settle), at);
    }

    private static FifoGainCalculator.Fill sell(String s, String q, String amt, String fee, String settle, String at) {
        return new FifoGainCalculator.Fill(s, false, new BigDecimal(q), new BigDecimal(amt), new BigDecimal(fee),
                BigDecimal.ZERO, LocalDate.parse(settle), at);
    }
}
