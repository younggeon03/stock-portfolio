package com.mystock.portfolio.service.institution;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static com.mystock.portfolio.service.institution.HoldingDiff.Kind;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 분기 비교. 숫자는 데모 값이고 기댓값은 손으로 셌다.
 */
class HoldingDiffTest {

    @Test
    void 새로_산_늘린_줄인_다_판_종목을_가른다() {
        List<HoldingDiff.Position> before = List.of(
                pos("AAA", 1000, "40.00"), pos("BBB", 500, "30.00"), pos("CCC", 200, "20.00"), pos("DDD", 100, "10.00"));
        List<HoldingDiff.Position> now = List.of(
                pos("AAA", 1500, "45.00"), pos("BBB", 400, "25.00"), pos("CCC", 200, "18.00"), pos("EEE", 300, "12.00"));

        List<HoldingDiff.Change> changes = HoldingDiff.diff(now, before);

        assertThat(changes).extracting(HoldingDiff.Change::cusip, HoldingDiff.Change::kind).containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple("AAA", Kind.ADDED),
                org.assertj.core.groups.Tuple.tuple("BBB", Kind.REDUCED),
                org.assertj.core.groups.Tuple.tuple("CCC", Kind.UNCHANGED),
                org.assertj.core.groups.Tuple.tuple("EEE", Kind.NEW),
                org.assertj.core.groups.Tuple.tuple("DDD", Kind.SOLD_OUT));
        HoldingDiff.Change aaa = changes.stream().filter(c -> c.cusip().equals("AAA")).findFirst().orElseThrow();
        assertThat(aaa.sharesChangePercent()).isEqualByComparingTo("50.00");
    }

    @Test
    void 큰_변화부터_나온다() {
        List<HoldingDiff.Change> changes = HoldingDiff.diff(
                List.of(pos("SMALL", 10, "2.00"), pos("BIG", 900, "60.00")),
                List.of(pos("GONE", 100, "30.00")));

        assertThat(changes).extracting(HoldingDiff.Change::cusip).containsExactly("BIG", "GONE", "SMALL");
    }

    @Test
    void 액면분할로_주식_수만_늘면_매수가_아니다() {
        // 10:1 분할. 주식 수는 10배인데 기관 안 비중은 그대로
        HoldingDiff.Change c = HoldingDiff.diff(
                List.of(pos("NVDA", 10_000, "8.10")), List.of(pos("NVDA", 1_000, "8.00"))).get(0);

        assertThat(c.kind()).isEqualTo(Kind.SPLIT);
    }

    @Test
    void 정확히_두_배를_더_사서_비중도_크게_오르면_분할이_아니라_매수다() {
        HoldingDiff.Change c = HoldingDiff.diff(
                List.of(pos("XYZ", 2_000, "20.00")), List.of(pos("XYZ", 1_000, "8.00"))).get(0);

        assertThat(c.kind()).isEqualTo(Kind.ADDED);
    }

    @Test
    void 분할_비율이_아니면_비중이_그대로여도_매수다() {
        // 1.37배. 흔한 분할 비율이 아니다
        HoldingDiff.Change c = HoldingDiff.diff(
                List.of(pos("XYZ", 1_370, "8.00")), List.of(pos("XYZ", 1_000, "8.00"))).get(0);

        assertThat(c.kind()).isEqualTo(Kind.ADDED);
    }

    @Test
    void 병합으로_주식_수가_줄어도_매도가_아니다() {
        // 1:20 역분할 (주식 병합)
        HoldingDiff.Change c = HoldingDiff.diff(
                List.of(pos("REV", 50, "3.00")), List.of(pos("REV", 1_000, "3.10"))).get(0);

        assertThat(c.kind()).isEqualTo(Kind.SPLIT);
    }

    @Test
    void 기관_절반_이상이_낸_가장_최근_분기를_고른다() {
        java.time.LocalDate q2 = java.time.LocalDate.of(2026, 6, 30);
        java.time.LocalDate q1 = java.time.LocalDate.of(2026, 3, 31);
        // 4곳 중 Q2 는 1곳만 냈고 Q1 은 4곳 모두 냈다 → Q1
        List<List<com.mystock.portfolio.domain.Filing13F>> filings = List.of(
                List.of(filing(q2), filing(q1)), List.of(filing(q1)), List.of(filing(q1)), List.of(filing(q1)));

        assertThat(InstitutionPortfolioService.defaultConsensusPeriod(filings, 4)).isEqualTo(q1);
    }

    @Test
    void 바로_앞_분기말을_구한다() {
        assertThat(InstitutionPortfolioService.previousQuarterEnd(java.time.LocalDate.of(2026, 6, 30)))
                .isEqualTo(java.time.LocalDate.of(2026, 3, 31));
        assertThat(InstitutionPortfolioService.previousQuarterEnd(java.time.LocalDate.of(2026, 3, 31)))
                .isEqualTo(java.time.LocalDate.of(2025, 12, 31));
        assertThat(InstitutionPortfolioService.previousQuarterEnd(java.time.LocalDate.of(2025, 12, 31)))
                .isEqualTo(java.time.LocalDate.of(2025, 9, 30));
    }

    private static HoldingDiff.Position pos(String cusip, long shares, String weight) {
        return new HoldingDiff.Position(cusip, cusip, cusip + " INC", shares, shares * 100, new BigDecimal(weight));
    }

    private static com.mystock.portfolio.domain.Filing13F filing(java.time.LocalDate period) {
        return new com.mystock.portfolio.domain.Filing13F("acc-" + period + "-" + Math.random(), 1, period,
                period.plusDays(45), 1, 1);
    }
}
