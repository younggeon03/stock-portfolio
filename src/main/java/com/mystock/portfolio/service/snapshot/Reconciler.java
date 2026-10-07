package com.mystock.portfolio.service.snapshot;

import com.mystock.portfolio.brokerage.Broker;
import com.mystock.portfolio.brokerage.BrokerageStatement;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 대사: 증권사가 밝힌 합계와 앱이 더한 값을 맞춰 본다.
 *
 * ★ 허용 오차
 * 증권사는 종목별 금액을 반올림해 보여주고 합계는 반올림 전 값으로 더하기도 해서, 원 단위까지 같지는 않다.
 * 그래서 "합계의 0.1% 또는 최소 단위(1원, 1센트) 중 큰 쪽" 까지는 맞은 것으로 본다.
 * 그보다 크면 반올림이 아니라 무언가 빠졌거나 더 들어간 것이다(걸러진 줄, 모르는 통화, 응답 모양 변경).
 * 0.1% 는 1억 원 계좌에서 10만 원이다. 종목 하나만 빠져도 대개 이보다 크다.
 */
public final class Reconciler {

    /** 합계 대비 허용 비율 */
    static final BigDecimal TOLERANCE_RATIO = new BigDecimal("0.001");

    private Reconciler() {
    }

    /** 대사 한 줄의 결과 */
    public record Line(Broker broker, String scope, String currency,
                       BigDecimal brokerTotal, BigDecimal appSum, BigDecimal difference, boolean matched) {
    }

    public static List<Line> reconcile(Map<Broker, BrokerageStatement> statements) {
        List<Line> lines = new ArrayList<>();
        statements.forEach((broker, statement) -> {
            for (BrokerageStatement.ReportedTotal t : statement.totals()) {
                BigDecimal brokerTotal = nvl(t.brokerTotal());
                BigDecimal appSum = nvl(t.appSum());
                BigDecimal diff = appSum.subtract(brokerTotal);
                lines.add(new Line(broker, t.scope(), t.currency(), brokerTotal, appSum, diff,
                        diff.abs().compareTo(tolerance(brokerTotal, t.currency())) <= 0));
            }
        });
        return lines;
    }

    /** 허용 오차 = max(합계 × 0.1%, 최소 단위) */
    static BigDecimal tolerance(BigDecimal brokerTotal, String currency) {
        BigDecimal unit = "USD".equalsIgnoreCase(currency) ? new BigDecimal("0.01") : BigDecimal.ONE;
        BigDecimal ratio = brokerTotal.abs().multiply(TOLERANCE_RATIO).setScale(4, RoundingMode.HALF_UP);
        return ratio.max(unit);
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
