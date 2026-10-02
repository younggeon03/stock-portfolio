package com.mystock.portfolio.service.dividend;

import com.mystock.portfolio.domain.DividendEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 배당 캘린더의 고르기·합치기 규칙. DART·DB 는 부르지 않는다. 숫자는 데모 값 */
class DividendServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 2);

    @Test
    void 배당결정_공시만_고르고_자회사_배당은_뺀다() {
        assertThat(DividendService.isDividendDecision("현금ㆍ현물배당결정")).isTrue();
        assertThat(DividendService.isDividendDecision("[기재정정]현금ㆍ현물배당결정")).isTrue();
        assertThat(DividendService.isDividendDecision("현금ㆍ현물 배당 결정")).isTrue();
        assertThat(DividendService.isDividendDecision("주요종속회사의현금배당결정")).isFalse();
        assertThat(DividendService.isDividendDecision("유상증자결정")).isFalse();
    }

    @Test
    void 같은_기준일의_정정_공시가_원본을_이긴다() {
        DividendEvent original = event("20260730800137", "2026-06-30", "374", false);
        DividendEvent corrected = event("20260805800010", "2026-06-30", "375", true);
        DividendEvent older = event("20260430800106", "2026-03-31", "370", false);

        List<DividendEvent> picked = DividendService.latestPerRecord(List.of(original, older, corrected));

        assertThat(picked).extracting(DividendEvent::getRceptNo).containsExactly("20260805800010", "20260430800106");
    }

    @Test
    void 지급일이_오늘_이후면_다가오는_배당이고_보유_수량으로_세전_금액을_낸다() {
        DividendEvent e = new DividendEvent("r1", "005930", "삼성전자", "분기배당", "현금배당", new BigDecimal("374"),
                null, null, LocalDate.of(2026, 9, 30), LocalDate.of(2026, 11, 20), null, LocalDate.of(2026, 10, 29), false);

        DividendService.Payment p = DividendService.payment(e, 10L, TODAY);

        assertThat(p.upcoming()).isTrue();
        assertThat(p.expectedAmount()).isEqualByComparingTo("3740");
        assertThat(p.sourceUrl()).endsWith("rcpNo=r1");
    }

    @Test
    void 이미_지급된_배당은_다가오는_배당이_아니다() {
        DividendService.Payment p = DividendService.payment(event("r2", "2026-03-31", "370", false), null, TODAY);

        assertThat(p.upcoming()).isFalse();
        assertThat(p.expectedAmount()).isNull();
    }

    @Test
    void 최근_1년_주당_배당은_기준일이_1년_안인_것만_더한다() {
        List<DividendService.Payment> payments = List.of(
                DividendService.payment(event("a", "2026-06-30", "374", false), null, TODAY),
                DividendService.payment(event("b", "2026-03-31", "370", false), null, TODAY),
                DividendService.payment(event("c", "2025-06-30", "361", false), null, TODAY));

        assertThat(DividendService.trailingPerShare(payments, TODAY)).isEqualByComparingTo("744");
    }

    private static DividendEvent event(String rcept, String record, String perShare, boolean correction) {
        LocalDate r = LocalDate.parse(record);
        return new DividendEvent(rcept, "005930", "삼성전자", "분기배당", "현금배당", new BigDecimal(perShare), null, null,
                r, r.plusDays(59), r.plusDays(30), r.plusDays(30), correction);
    }
}
