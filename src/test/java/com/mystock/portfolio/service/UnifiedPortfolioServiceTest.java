package com.mystock.portfolio.service;

import com.mystock.portfolio.brokerage.Broker;
import com.mystock.portfolio.brokerage.BrokerageClient;
import com.mystock.portfolio.brokerage.BrokerageHolding;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이 앱의 존재 이유인 "두 증권사를 합친 진짜 비중".
 *
 * 증권사 API 는 부르지 않는다. BrokerageClient 를 가짜로 넣어 합산·비중·평단가 계산만 본다.
 * 숫자는 전부 데모 값이고, 기댓값은 손으로 계산했다.
 */
class UnifiedPortfolioServiceTest {

    @Test
    void 같은_종목은_증권사를_넘어_한_줄로_합치고_비중은_합산_기준이다() {
        // 토스 SOXL 30만원 + 나무 SOXL 970만원 + 나무 AVGO 200만원 = 1,200만원
        var service = new UnifiedPortfolioService(List.of(
                client(Broker.TOSS, soxl(Broker.TOSS, "2", "300000", "280000")),
                client(Broker.NAMUH, soxl(Broker.NAMUH, "60", "9700000", "9000000"),
                        holding(Broker.NAMUH, "AVGO", "5", "400", "2000000", "1800000"))));

        UnifiedPortfolioView view = service.load("ALL", "owner");

        assertThat(view.items()).extracting(UnifiedPortfolioView.Item::symbol).containsExactly("SOXL", "AVGO");
        UnifiedPortfolioView.Item soxl = view.items().get(0);
        assertThat(soxl.quantity()).isEqualByComparingTo("62");
        assertThat(soxl.marketValueKrw()).isEqualByComparingTo("10000000");
        // 토스 화면만 보면 SOXL 은 토스 자산의 100% 지만, 합치면 전체의 83.33%
        assertThat(soxl.weightPercent()).isEqualByComparingTo("83.33");
        assertThat(soxl.lots()).extracting(UnifiedPortfolioView.Lot::broker).containsExactly("TOSS", "NAMUH");

        assertThat(view.totalValueKrw()).isEqualByComparingTo("12000000");
        // 매입 28만 + 900만 + 180만 = 1,108만, 손익 92만, 수익률 92 / 1108 = 8.30%
        assertThat(view.totalPurchaseKrw()).isEqualByComparingTo("11080000");
        assertThat(view.totalProfitLossKrw()).isEqualByComparingTo("920000");
        assertThat(view.totalProfitRatePercent()).isEqualByComparingTo("8.30");
    }

    @Test
    void 증권사_비중도_합산_기준으로_채운다() {
        var service = new UnifiedPortfolioService(List.of(
                client(Broker.TOSS, soxl(Broker.TOSS, "2", "300000", "280000")),
                client(Broker.NAMUH, soxl(Broker.NAMUH, "60", "9700000", "9000000"))));

        UnifiedPortfolioView view = service.load("ALL", "owner");

        assertThat(view.brokers()).extracting(UnifiedPortfolioView.BrokerSummary::broker,
                        UnifiedPortfolioView.BrokerSummary::weightPercent)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("TOSS", new BigDecimal("3.00")),
                        org.assertj.core.groups.Tuple.tuple("NAMUH", new BigDecimal("97.00")));
    }

    @Test
    void 평단가는_수량으로_가중평균한다() {
        // 100주를 10달러에, 1주를 20달러에 샀다. 단순 평균(15)이 아니라 (1000 + 20) / 101
        var service = new UnifiedPortfolioService(List.of(
                client(Broker.NAMUH, withAvg(Broker.NAMUH, "100", "10")),
                client(Broker.TOSS, withAvg(Broker.TOSS, "1", "20"))));

        UnifiedPortfolioView.Item item = service.load("ALL", "owner").items().get(0);

        assertThat(item.averagePurchasePrice()).isEqualByComparingTo("10.0990");
    }

    @Test
    void 평단가를_모르는_물량은_가중평균에서_빼고_전부_모르면_비워둔다() {
        var partly = new UnifiedPortfolioService(List.of(
                client(Broker.NAMUH, withAvg(Broker.NAMUH, "10", "30")),
                client(Broker.MANUAL, withAvg(Broker.MANUAL, "5", null))));
        assertThat(partly.load("ALL", "owner").items().get(0).averagePurchasePrice()).isEqualByComparingTo("30");

        // 0 으로 채우면 "공짜로 샀다" 는 거짓말이 된다
        var none = new UnifiedPortfolioService(List.of(client(Broker.MANUAL, withAvg(Broker.MANUAL, "5", null))));
        assertThat(none.load("ALL", "owner").items().get(0).averagePurchasePrice()).isNull();
    }

    @Test
    void 한_증권사가_실패해도_나머지는_보이고_요약에_이유가_남는다() {
        var service = new UnifiedPortfolioService(List.of(
                failing(Broker.TOSS, "토스증권 토큰 발급 실패 (HTTP 403)"),
                client(Broker.NAMUH, soxl(Broker.NAMUH, "60", "9700000", "9000000"))));

        UnifiedPortfolioView view = service.load("ALL", "owner");

        assertThat(view.items()).hasSize(1);
        assertThat(view.items().get(0).weightPercent()).isEqualByComparingTo("100");
        UnifiedPortfolioView.BrokerSummary toss = view.brokers().get(0);
        assertThat(toss.configured()).isTrue();
        assertThat(toss.error()).contains("403");
    }

    @Test
    void 키가_없는_증권사는_부르지_않고_설정_안_됨으로_표시한다() {
        var service = new UnifiedPortfolioService(List.of(
                notConfigured(Broker.TOSS),
                client(Broker.NAMUH, soxl(Broker.NAMUH, "60", "9700000", "9000000"))));

        UnifiedPortfolioView.BrokerSummary toss = service.load("ALL", "owner").brokers().get(0);

        assertThat(toss.configured()).isFalse();
        assertThat(toss.error()).contains(".env");
    }

    @Test
    void 범위를_정하면_그_출처만_합치고_모르는_값이면_전체로_본다() {
        var service = new UnifiedPortfolioService(List.of(
                client(Broker.TOSS, soxl(Broker.TOSS, "2", "300000", "280000")),
                client(Broker.NAMUH, soxl(Broker.NAMUH, "60", "9700000", "9000000"))));

        UnifiedPortfolioView namuh = service.load("namuh", "owner");
        assertThat(namuh.scope()).isEqualTo("NAMUH");
        assertThat(namuh.totalValueKrw()).isEqualByComparingTo("9700000");
        assertThat(namuh.brokers()).extracting(UnifiedPortfolioView.BrokerSummary::broker).containsExactly("NAMUH");

        // 오타로 빈 화면이 뜨는 것보다 전체를 보여주는 게 낫다
        assertThat(service.load("NAMU", "owner").scope()).isEqualTo("ALL");
        assertThat(service.load(null, "owner").totalValueKrw()).isEqualByComparingTo("10000000");
    }

    @Test
    void 직접_입력분이_없으면_요약에_0원으로_끼어들지_않는다() {
        var service = new UnifiedPortfolioService(List.of(
                client(Broker.NAMUH, soxl(Broker.NAMUH, "60", "9700000", "9000000")),
                client(Broker.MANUAL)));

        assertThat(service.load("ALL", "owner").brokers())
                .extracting(UnifiedPortfolioView.BrokerSummary::broker).containsExactly("NAMUH");
    }

    @Test
    void 보유종목이_하나도_없어도_0으로_나누지_않는다() {
        var service = new UnifiedPortfolioService(List.of(client(Broker.NAMUH)));

        UnifiedPortfolioView view = service.load("ALL", "owner");

        assertThat(view.items()).isEmpty();
        assertThat(view.totalProfitRatePercent()).isEqualByComparingTo("0");
        assertThat(view.brokers().get(0).weightPercent()).isEqualByComparingTo("0");
    }

    // ── 가짜 증권사 ─────────────────────────────────

    private static BrokerageHolding soxl(Broker broker, String qty, String valueKrw, String purchaseKrw) {
        return holding(broker, "SOXL", qty, "120", valueKrw, purchaseKrw);
    }

    private static BrokerageHolding holding(Broker broker, String symbol, String qty, String avg,
                                            String valueKrw, String purchaseKrw) {
        return new BrokerageHolding(broker, symbol, symbol, "US", "USD", new BigDecimal(qty), new BigDecimal("145.70"),
                avg == null ? null : new BigDecimal(avg), new BigDecimal(valueKrw), new BigDecimal(purchaseKrw));
    }

    private static BrokerageHolding withAvg(Broker broker, String qty, String avg) {
        return holding(broker, "SOXL", qty, avg, "1000000", "900000");
    }

    private static BrokerageClient client(Broker broker, BrokerageHolding... holdings) {
        return new FakeClient(broker, true, List.of(holdings), null);
    }

    private static BrokerageClient failing(Broker broker, String message) {
        return new FakeClient(broker, true, List.of(), message);
    }

    private static BrokerageClient notConfigured(Broker broker) {
        return new FakeClient(broker, false, List.of(), null);
    }

    private record FakeClient(Broker broker, boolean configured, List<BrokerageHolding> list, String failure)
            implements BrokerageClient {

        @Override
        public boolean isConfigured() {
            return configured;
        }

        @Override
        public List<BrokerageHolding> holdings(String ownerKey) {
            if (failure != null) {
                throw new IllegalStateException(failure);
            }
            return list;
        }
    }
}
