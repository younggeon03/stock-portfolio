package com.mystock.portfolio.service.institution;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 새 13F 알림 글과 "알릴 만한가" 판단. 숫자는 데모 값이다 */
class FilingAlertsTest {

    private static final LocalDate Q2 = LocalDate.of(2026, 6, 30);
    private static final LocalDate FILED = LocalDate.of(2026, 8, 14);

    @Test
    void 알림_글에_분기_변화와_링크와_한계가_들어간다() {
        InstitutionPortfolioService.ChangesView changes = new InstitutionPortfolioService.ChangesView(
                null, Q2, LocalDate.of(2026, 3, 31), Map.of(),
                List.of(change(HoldingDiff.Kind.ADDED, "GOOGL", "45.24"), change(HoldingDiff.Kind.NEW, "LLY", null),
                        change(HoldingDiff.Kind.REDUCED, "KR", "-22.00"), change(HoldingDiff.Kind.SOLD_OUT, "HPQ", null)), 4);

        String text = FilingAlerts.body("버크셔 해서웨이", changes, Q2, FILED, 1067983, "https://example.com/");

        assertThat(text).startsWith("버크셔 해서웨이 2026년 2분기 13F 공개 (2026-08-14 공개)")
                .contains("새로 삼: LLY")
                .contains("늘림: GOOGL +45%")
                .contains("줄임: KR -22%")
                .contains("다 팖: HPQ")
                .contains("https://example.com/public/institution.html?cik=1067983&period=2026-06-30")
                .contains("매매 권유가 아닙니다");
    }

    @Test
    void 종류마다_세_개까지만_적고_나머지는_개수로() {
        List<HoldingDiff.Change> many = List.of("A", "B", "C", "D", "E").stream()
                .map(t -> change(HoldingDiff.Kind.ADDED, t, "10")).toList();
        String text = FilingAlerts.body("기관", new InstitutionPortfolioService.ChangesView(null, Q2, Q2.minusMonths(3),
                Map.of(), many, many.size()), Q2, FILED, 1, "http://localhost:8080");

        assertThat(text).contains("늘림: A +10%, B +10%, C +10% 외 2");
    }

    @Test
    void 공개된_지_사흘_안이고_보유가_있을_때만_알린다() {
        LocalDate today = LocalDate.of(2026, 8, 16);

        assertThat(FilingAlerts.worthAlerting(LocalDate.of(2026, 8, 14), 29, today, 3)).isTrue();
        // 처음 8분기를 받을 때 옛 분기가 쏟아지면 안 된다
        assertThat(FilingAlerts.worthAlerting(LocalDate.of(2026, 5, 15), 29, today, 3)).isFalse();
        // 노르웨이 중앙은행의 비공개 빈 제출
        assertThat(FilingAlerts.worthAlerting(LocalDate.of(2026, 8, 14), 0, today, 3)).isFalse();
    }

    private static HoldingDiff.Change change(HoldingDiff.Kind kind, String ticker, String pct) {
        return new HoldingDiff.Change(kind, ticker, ticker, ticker + " INC", 100, 145,
                pct == null ? null : new BigDecimal(pct), 1000, BigDecimal.ONE, BigDecimal.TEN);
    }
}
