package com.mystock.portfolio.service.institution;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.Order;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 예열이 화면과 같은 인자로 조회를 부르는지, 실패해도 조용한지 */
class InstitutionCacheWarmerTest {

    private static InstitutionPortfolioService.InstitutionView inst(long cik) {
        return new InstitutionPortfolioService.InstitutionView(cik, "기관" + cik, "Inst", null, null,
                LocalDate.of(2026, 6, 30), LocalDate.of(2026, 8, 14), 1L, 1, List.of());
    }

    @Test
    void 첫_화면과_기관_상세가_쓰는_조회를_같은_인자로_부른다() {
        InstitutionPortfolioService service = mock(InstitutionPortfolioService.class);
        when(service.list()).thenReturn(List.of(inst(1L), inst(2L)));

        new InstitutionCacheWarmer(service).warm();

        // 캐시 키가 요청 모양과 같아야 맞는다. home.js 는 limit=5, 기본은 30, period 는 비움
        verify(service).consensus(isNull(), org.mockito.ArgumentMatchers.eq(5));
        verify(service).consensus(isNull(), org.mockito.ArgumentMatchers.eq(30));
        verify(service).holdings(1L, null);
        verify(service).changes(1L, null);
        verify(service).holdings(2L, null);
        verify(service).changes(2L, null);
    }

    @Test
    void 중간에_실패해도_예외를_내지_않는다() {
        InstitutionPortfolioService service = mock(InstitutionPortfolioService.class);
        when(service.consensus(isNull(), anyInt())).thenThrow(new IllegalStateException("DB 끊김"));

        assertThatCode(() -> new InstitutionCacheWarmer(service).warm()).doesNotThrowAnyException();
        verify(service, never()).list();
    }

    @Test
    void 비우기가_예열보다_먼저_돈다() throws Exception {
        int clear = InstitutionPortfolioService.class.getMethod("onDataChanged").getAnnotation(Order.class).value();
        int warm = InstitutionCacheWarmer.class.getMethod("onBatchDone").getAnnotation(Order.class).value();
        assertThat(clear).isLessThan(warm);
    }
}
