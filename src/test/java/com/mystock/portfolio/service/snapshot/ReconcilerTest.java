package com.mystock.portfolio.service.snapshot;

import com.mystock.portfolio.brokerage.Broker;
import com.mystock.portfolio.brokerage.BrokerageStatement;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 대사 허용 오차: 합계의 0.1% 또는 최소 단위(1원·1센트) 중 큰 쪽. 금액은 데모 값 */
class ReconcilerTest {

    private static Map<Broker, BrokerageStatement> one(String currency, String brokerTotal, String appSum) {
        Map<Broker, BrokerageStatement> m = new LinkedHashMap<>();
        m.put(Broker.TOSS, new BrokerageStatement(List.of(), List.of(new BrokerageStatement.ReportedTotal(
                currency, currency, new BigDecimal(brokerTotal), new BigDecimal(appSum)))));
        return m;
    }

    @Test
    void 반올림_정도의_차이는_맞은_것으로() {
        // 1억 원의 0.1% = 10만 원까지
        assertThat(Reconciler.reconcile(one("KRW", "100000000", "100099999")).get(0).matched()).isTrue();
        assertThat(Reconciler.reconcile(one("KRW", "100000000", "100100001")).get(0).matched()).isFalse();
    }

    @Test
    void 작은_계좌는_최소_단위까지만_봐준다() {
        // 500원의 0.1% 는 0.5원이라 최소 단위 1원이 기준
        assertThat(Reconciler.reconcile(one("KRW", "500", "501")).get(0).matched()).isTrue();
        assertThat(Reconciler.reconcile(one("KRW", "500", "502")).get(0).matched()).isFalse();
        assertThat(Reconciler.tolerance(new BigDecimal("3"), "USD")).isEqualByComparingTo("0.01");
    }

    @Test
    void 차이는_앱_빼기_증권사로_적는다() {
        Reconciler.Line line = Reconciler.reconcile(one("USD", "1500.00", "1450.25")).get(0);

        assertThat(line.difference()).isEqualByComparingTo("-49.75");
        assertThat(line.broker()).isEqualTo(Broker.TOSS);
        assertThat(line.matched()).isFalse();
    }

    @Test
    void 합계를_안_주는_곳은_대사_줄이_없다() {
        Map<Broker, BrokerageStatement> m = Map.of(Broker.MANUAL, BrokerageStatement.withoutTotals(List.of()));
        assertThat(Reconciler.reconcile(m)).isEmpty();
    }
}
