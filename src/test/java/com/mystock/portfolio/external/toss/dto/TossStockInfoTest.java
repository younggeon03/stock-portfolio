package com.mystock.portfolio.external.toss.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 종목 유형 판별 테스트.
 *
 * ★ 왜 이걸 테스트하는가
 * ETF 에 PER·ROE 를 물어보면 클로드가 없는 숫자를 지어낼 위험이 있다.
 * 그래서 "이건 ETF 다" 를 LLM 판단이 아니라 토스가 준 확정값으로 가른다.
 * 이 판별이 틀리면 분석 전체가 엉망이 되므로 테스트로 고정해둔다.
 */
class TossStockInfoTest {

    @Test
    void ETF_는_펀드형으로_판별된다() {
        assertThat(stock("ETF", null).isFund()).isTrue();
        assertThat(stock("FOREIGN_ETF", null).isFund()).isTrue();
        assertThat(stock("ETN", null).isFund()).isTrue();
        assertThat(stock("REIT", null).isFund()).isTrue();
    }

    @Test
    void 일반_주식은_펀드형이_아니다() {
        assertThat(stock("STOCK", null).isFund()).isFalse();
        assertThat(stock("FOREIGN_STOCK", null).isFund()).isFalse();
    }

    @Test
    void 종목유형을_모르면_펀드형으로_보지_않는다() {
        assertThat(stock(null, null).isFund()).isFalse();
    }

    @Test
    void 레버리지_배수가_1을_넘으면_레버리지_상품이다() {
        // SOXL 은 3배
        assertThat(stock("ETF", new BigDecimal("3.0")).isLeveraged()).isTrue();
        assertThat(stock("ETF", new BigDecimal("2.0")).isLeveraged()).isTrue();
    }

    @Test
    void 인버스도_레버리지_상품으로_본다() {
        // -1배(인버스)도 방향만 반대일 뿐 일반 주식과 성격이 다르다.
        // 절대값으로 보므로 -2배는 레버리지, -1배는 아니다.
        assertThat(stock("ETF", new BigDecimal("-2.0")).isLeveraged()).isTrue();
        assertThat(stock("ETF", new BigDecimal("-1.0")).isLeveraged()).isFalse();
    }

    @Test
    void 배수가_없거나_1배면_레버리지가_아니다() {
        assertThat(stock("STOCK", null).isLeveraged()).isFalse();
        assertThat(stock("ETF", BigDecimal.ONE).isLeveraged()).isFalse();
    }

    @Test
    void 정리매매와_거래정지를_구분해서_판별한다() {
        TossStockInfo liquidating = withKoreanDetail(true, false);
        assertThat(liquidating.isLiquidationTrading()).isTrue();
        assertThat(liquidating.isTradingSuspended()).isFalse();

        TossStockInfo suspended = withKoreanDetail(false, true);
        assertThat(suspended.isLiquidationTrading()).isFalse();
        assertThat(suspended.isTradingSuspended()).isTrue();
    }

    @Test
    void 해외종목은_국내_상세정보가_없어도_안전하게_false_다() {
        // 미국 종목은 koreanMarketDetail 이 null 로 온다. NullPointerException 이 나면 안 된다.
        TossStockInfo us = stock("STOCK", null);
        assertThat(us.isLiquidationTrading()).isFalse();
        assertThat(us.isTradingSuspended()).isFalse();
    }

    @Test
    void 상장폐지_상태나_폐지일이_있으면_폐지로_본다() {
        assertThat(delisting("DELISTED", null).isDelisting()).isTrue();
        assertThat(delisting("ACTIVE", "2026-12-31").isDelisting()).isTrue();
        assertThat(delisting("ACTIVE", null).isDelisting()).isFalse();
    }

    // ── 테스트용 객체 만들기 ──────────────────────────────

    private TossStockInfo stock(String securityType, BigDecimal leverageFactor) {
        return new TossStockInfo("TEST", "테스트종목", "Test", "KR0000000000", "NASDAQ",
                securityType, true, "ACTIVE", "USD", null, null,
                new BigDecimal("1000000"), leverageFactor, null);
    }

    private TossStockInfo withKoreanDetail(boolean liquidation, boolean suspended) {
        return new TossStockInfo("005930", "삼성전자", "SamsungElec", "KR7005930003", "KOSPI",
                "STOCK", true, "ACTIVE", "KRW", "1975-06-11", null,
                new BigDecimal("5919637922"), null,
                new TossStockInfo.KoreanMarketDetail(liquidation, true, suspended, false));
    }

    private TossStockInfo delisting(String status, String delistDate) {
        return new TossStockInfo("TEST", "테스트종목", "Test", "KR0000000000", "KOSPI",
                "STOCK", true, status, "KRW", null, delistDate,
                new BigDecimal("1000000"), null, null);
    }
}
