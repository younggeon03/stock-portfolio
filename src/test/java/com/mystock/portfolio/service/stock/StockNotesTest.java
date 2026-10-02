package com.mystock.portfolio.service.stock;

import com.mystock.portfolio.external.filing.CompanyFinancials;
import com.mystock.portfolio.external.filing.CompanyFinancials.Period;
import com.mystock.portfolio.external.filing.CompanyFinancials.Valuation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 공개 종목 창의 문장 규칙. 숫자는 마이크로소프트 10-K (FY2024~FY2026, 억 달러 단위로 줄임).
 * 문장이 바뀌면 화면에 나가는 말이 바뀌는 것이라 테스트로 고정한다.
 */
class StockNotesTest {

    private static Period year(String label, long revenue, long op, long net, double debtRatio, double margin) {
        return new Period(label, bd(revenue), bd(op), bd(net), null, null, null, null, null,
                BigDecimal.valueOf(debtRatio), BigDecimal.valueOf(margin), null, null, null, null);
    }

    private static BigDecimal bd(long v) {
        return BigDecimal.valueOf(v);
    }

    private static final List<Period> MSFT_YEARS = List.of(
            year("FY2024 (2024-06-30 결산)", 2451, 1094, 881, 90.77, 44.64),
            year("FY2025 (2025-06-30 결산)", 2817, 1285, 1018, 80.22, 45.62),
            year("FY2026 (2026-06-30 결산)", 3318, 1552, 1337, 71.43, 46.78));

    private static CompanyFinancials msft(BigDecimal per) {
        List<Valuation> history = List.of(
                new Valuation("FY2024", LocalDate.of(2024, 6, 28), BigDecimal.valueOf(446.95), BigDecimal.valueOf(37.65), null),
                new Valuation("FY2025", LocalDate.of(2025, 6, 30), BigDecimal.valueOf(497.41), BigDecimal.valueOf(36.28), null),
                new Valuation("FY2026", LocalDate.of(2026, 6, 30), BigDecimal.valueOf(373.02), BigDecimal.valueOf(20.71), null));
        return new CompanyFinancials("MICROSOFT CORPORATION", "SEC EDGAR", "USD", "연결", MSFT_YEARS, null, null,
                per, BigDecimal.valueOf(8.69), "보통주", history, List.of());
    }

    private static Map<String, String> byKind(List<StockNotes.Note> notes) {
        return notes.stream().collect(Collectors.toMap(StockNotes.Note::kind, StockNotes.Note::text));
    }

    @Test
    void 매출_성장이_빨라진_것을_말한다() {
        String growth = byKind(StockNotes.of(msft(null), null)).get("GROWTH");
        assertThat(growth).isEqualTo("FY2025 +14.9%, FY2026 +17.8%. 성장이 빨라지고 있습니다.");
    }

    @Test
    void 영업이익률_추이를_전부_보여준다() {
        assertThat(byKind(StockNotes.of(msft(null), null)).get("MARGIN"))
                .startsWith("44.6% → 45.6% → 46.8%.")
                .contains("더 많이 남는");
    }

    /** 순이익/영업이익 79% → 86%. 7%p 뛰었으니 영업 밖 이익을 확인하라고 해야 한다 */
    @Test
    void 순이익이_영업이익보다_빨리_늘면_확인하라고_한다() {
        assertThat(byKind(StockNotes.of(msft(null), null)).get("EARNINGS_QUALITY"))
                .contains("79% → 86%")
                .contains("영업외손익과 법인세를 확인");
    }

    @Test
    void 순이익과_영업이익이_같이_움직이면_말하지_않는다() {
        List<Period> steady = List.of(
                year("FY1", 1000, 400, 320, 50, 40),
                year("FY2", 1100, 440, 350, 50, 40));
        CompanyFinancials f = new CompanyFinancials("X", "SEC EDGAR", "USD", "연결", steady, null, null,
                null, null, null, List.of(), List.of());
        assertThat(byKind(StockNotes.of(f, null))).doesNotContainKey("EARNINGS_QUALITY");
    }

    @Test
    void 지금_PER_이_지난_범위_안인지_말한다() {
        assertThat(byKind(StockNotes.of(msft(BigDecimal.valueOf(28.76)), null)).get("VALUATION"))
                .isEqualTo("지금 PER 28.8, PBR 8.7. 지난 결산일들의 PER 은 37.7 → 36.3 → 20.7. 지난 범위 안입니다.");
    }

    @Test
    void 결산일_뒤_주가가_크게_움직이면_알린다() {
        BigDecimal change = StockBriefService.priceChangeSinceFiscalEnd(msft(null), BigDecimal.valueOf(518));
        assertThat(change).isEqualByComparingTo("38.9");
        assertThat(byKind(StockNotes.of(msft(null), change)).get("PRICE"))
                .startsWith("마지막 결산일(2026-06-30) 뒤로 주가가 +38.9% 움직였습니다.");
    }

    @Test
    void 주가가_조금_움직이면_말하지_않는다() {
        assertThat(byKind(StockNotes.of(msft(null), BigDecimal.valueOf(5)))).doesNotContainKey("PRICE");
    }

    @Test
    void 판정이나_적정가_말은_하지_않는다() {
        String all = StockNotes.of(msft(BigDecimal.valueOf(28.76)), BigDecimal.valueOf(38.9)).toString();
        assertThat(all).doesNotContain("매수", "매도", "적정가", "목표가", "싸다", "비싸다");
    }

    @Test
    void 한_줄_요약은_최근_해의_매출과_이익률() {
        assertThat(StockNotes.summary(msft(null))).isEqualTo("FY2026 매출 +17.8%, 영업이익률 46.8%");
    }

    @Test
    void 적자에서의_증가율은_만들지_않는다() {
        assertThat(StockNotes.change(BigDecimal.valueOf(-10), BigDecimal.valueOf(5))).isNull();
    }

    @Test
    void 티커_모양만_받는다() {
        assertThat(StockBriefService.validTicker("MSFT")).isTrue();
        assertThat(StockBriefService.validTicker("BRK.B")).isTrue();
        assertThat(StockBriefService.validTicker("005930")).isFalse();
        assertThat(StockBriefService.validTicker("../etc")).isFalse();
        assertThat(StockBriefService.validTicker("ABCDEFGHIJKL")).isFalse();
    }
}
