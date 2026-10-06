package com.mystock.portfolio.service.analysis;

import com.mystock.portfolio.external.filing.CompanyFinancials;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 앱이 공시 재무로 재무 섹션 표를 끼우는 규칙. 숫자는 데모 값 */
class FinancialMetricsTest {

    private static CompanyFinancials.Period year(String label, long revenueEok, int y) {
        BigDecimal eok = BigDecimal.valueOf(100_000_000L);
        return CompanyFinancials.Period.of(label,
                BigDecimal.valueOf(revenueEok).multiply(eok), BigDecimal.valueOf(revenueEok / 10).multiply(eok),
                BigDecimal.valueOf(revenueEok / 20).multiply(eok),
                BigDecimal.valueOf(revenueEok * 2).multiply(eok), BigDecimal.valueOf(revenueEok / 2).multiply(eok),
                BigDecimal.valueOf(revenueEok * 3 / 2).multiply(eok),
                true, BigDecimal.valueOf(100_000_000L), 0).withEnd(LocalDate.of(y, 12, 31));
    }

    private static CompanyFinancials financials() {
        List<CompanyFinancials.Period> years = List.of(
                year("2022년", 8_000, 2022), year("2023년", 9_000, 2023),
                year("2024년", 10_000, 2024), year("2025년", 12_000, 2025));
        CompanyFinancials f = CompanyFinancials.of("데모전자", "DART", "KRW", "연결", years, null, null,
                new BigDecimal("50000"), "보통주", List.of(new CompanyFinancials.Source("DART 2025년 사업보고서", "https://dart.example/1")));
        return f.withHistory(List.of(
                CompanyFinancials.Valuation.of(years.get(2), LocalDate.of(2024, 12, 30), new BigDecimal("40000")),
                CompanyFinancials.Valuation.of(years.get(3), LocalDate.of(2025, 12, 30), new BigDecimal("45000"))));
    }

    private static CompanyAnalysisView view(CompanyAnalysisView.Section... sections) {
        return new CompanyAnalysisView("000000", "데모전자", "STOCK", "요약", null, List.of(sections), List.of());
    }

    private static CompanyAnalysisView.Section section(String key, boolean applicable,
                                                       List<CompanyAnalysisView.Metric> metrics,
                                                       List<CompanyAnalysisView.Source> sources) {
        return new CompanyAnalysisView.Section(key, key, applicable, "", "본문", List.of(), metrics, sources);
    }

    @Test
    void 재무상태_표는_최근_3년을_최신부터_조원_억원으로() {
        CompanyAnalysisView out = FinancialMetrics.inject(
                view(section("FINANCIAL_POSITION", true, List.of(), List.of())), financials());

        CompanyAnalysisView.Section s = out.sections().get(0);
        CompanyAnalysisView.Metric revenue = s.metrics().get(0);
        assertThat(revenue.label()).isEqualTo("매출");
        assertThat(revenue.points()).extracting(CompanyAnalysisView.Metric.Point::period)
                .containsExactly("2025년", "2024년", "2023년");
        assertThat(revenue.points().get(0).value()).isEqualTo("1.2조원");
        assertThat(revenue.points().get(0).periodType()).isEqualTo("ANNUAL");
        assertThat(s.metrics().get(1).points().get(0).value()).isEqualTo("1,200억원");   // 영업이익
        // 공시 원문이 출처로 붙고 모든 값이 그 번호를 가리킨다
        assertThat(s.sources()).extracting(CompanyAnalysisView.Source::url).containsExactly("https://dart.example/1");
        assertThat(revenue.points()).allMatch(p -> p.sourceIndex() == 0);
    }

    @Test
    void 수치지표는_과거_배수와_현재가_배수_그리고_모델의_적정가를_뒤에() {
        CompanyAnalysisView.Metric fair = new CompanyAnalysisView.Metric("적정가 (2026년 이익 기준)", "",
                List.of(new CompanyAnalysisView.Metric.Point("2026", "POINT", "4만~5만원", 0)));
        CompanyAnalysisView.Source web = new CompanyAnalysisView.Source("리포트", "https://web.example", "증권사", "");

        CompanyAnalysisView out = FinancialMetrics.inject(
                view(section("VALUATION_METRICS", true, List.of(fair), List.of(web))), financials());

        CompanyAnalysisView.Section s = out.sections().get(0);
        assertThat(s.metrics()).extracting(CompanyAnalysisView.Metric::label)
                .containsExactly("PER", "PBR", "PER (현재가)", "PBR (현재가)", "ROE", "EPS", "BPS", "적정가 (2026년 이익 기준)");
        assertThat(s.metrics().get(0).points()).extracting(CompanyAnalysisView.Metric.Point::period)
                .containsExactly("2025년", "2024년");
        // 웹 출처는 0번 그대로, 공시는 1번으로 뒤에 붙는다
        assertThat(s.sources()).extracting(CompanyAnalysisView.Source::url)
                .containsExactly("https://web.example", "https://dart.example/1");
        assertThat(s.metrics().get(0).points().get(0).sourceIndex()).isEqualTo(1);
        assertThat(s.metrics().get(7).points().get(0).sourceIndex()).isEqualTo(0);
    }

    @Test
    void 모델이_같은_이름을_또_쓰면_공시_값을_남기고_같은_주소_출처는_재사용() {
        CompanyAnalysisView.Metric dup = new CompanyAnalysisView.Metric("매출", "",
                List.of(new CompanyAnalysisView.Metric.Point("2025", "ANNUAL", "틀린 값", 0)));
        CompanyAnalysisView.Source same = new CompanyAnalysisView.Source("사업보고서", "https://dart.example/1", "DART", "");

        CompanyAnalysisView.Section s = FinancialMetrics.inject(
                view(section("FINANCIAL_POSITION", true, List.of(dup), List.of(same))), financials()).sections().get(0);

        assertThat(s.metrics()).filteredOn(m -> m.label().equals("매출")).hasSize(1);
        assertThat(s.metrics().get(0).points().get(0).value()).isEqualTo("1.2조원");
        assertThat(s.sources()).hasSize(1);
    }

    @Test
    void 재무가_없거나_성립하지_않는_섹션은_그대로() {
        CompanyAnalysisView.Section etf = section("FINANCIAL_POSITION", false, List.of(), List.of());
        CompanyAnalysisView v = view(etf, section("BUSINESS_ANALYSIS", true, List.of(), List.of()));

        assertThat(FinancialMetrics.inject(v, null)).isSameAs(v);
        assertThat(FinancialMetrics.inject(v, financials()).sections()).containsExactlyElementsOf(v.sections());
    }

    @Test
    void 달러는_B와_M() {
        assertThat(FinancialMetrics.money(new BigDecimal("63887000000"), true)).isEqualTo("$63.9B");
        assertThat(FinancialMetrics.money(new BigDecimal("512000000"), true)).isEqualTo("$512M");
        assertThat(FinancialMetrics.money(new BigDecimal("-3000000000000"), false)).isEqualTo("-3조원");
    }
}
