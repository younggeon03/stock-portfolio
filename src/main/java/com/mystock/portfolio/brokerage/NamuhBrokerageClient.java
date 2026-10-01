package com.mystock.portfolio.brokerage;

import com.mystock.portfolio.external.namuh.NamuhApiProperties;
import com.mystock.portfolio.external.namuh.NamuhHoldingsService;
import com.mystock.portfolio.external.namuh.dto.NamuhGbBalanceResponse;
import com.mystock.portfolio.external.namuh.dto.NamuhKrBalanceResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 나무증권을 표준 모델로 감싸는 어댑터.
 *
 * 나무는 국내 주식과 해외 주식이 별도 엔드포인트라 두 번 조회해서 합친다.
 *
 * ★ 환율 환산이 편하다
 * 해외 잔고에 원화 환산 금액(krw_eal_amt)이 이미 들어있어서, 우리가 환율을 곱할 필요가 없다.
 * 국내 주식은 당연히 원화라 그대로 쓴다.
 */
@Component
public class NamuhBrokerageClient implements BrokerageClient {

    private static final Logger log = LoggerFactory.getLogger(NamuhBrokerageClient.class);

    private final NamuhHoldingsService holdingsService;
    private final NamuhApiProperties properties;

    public NamuhBrokerageClient(NamuhHoldingsService holdingsService, NamuhApiProperties properties) {
        this.holdingsService = holdingsService;
        this.properties = properties;
    }

    @Override
    public Broker broker() {
        return Broker.NAMUH;
    }

    @Override
    public boolean isConfigured() {
        return properties.hasCredentials();
    }

    /**
     * @param ownerKey 무시한다. 나무도 API 키가 곧 신분이다.
     */
    @Override
    public List<BrokerageHolding> holdings(String ownerKey) {
        List<BrokerageHolding> all = new ArrayList<>();
        all.addAll(domesticHoldings());
        all.addAll(overseasHoldings());
        return all;
    }

    /** 국내 주식. 전부 원화라 환산이 필요 없다. */
    private List<BrokerageHolding> domesticHoldings() {
        NamuhKrBalanceResponse response = holdingsService.domesticBalance(null);
        if (response.items() == null) {
            return List.of();
        }

        return response.items().stream()
                .filter(item -> item.symbol() != null)
                .filter(item -> nvl(item.quantity()).signum() > 0)
                .map(item -> new BrokerageHolding(
                        Broker.NAMUH,
                        item.symbol(),
                        item.name(),
                        "KR",
                        "KRW",
                        nvl(item.quantity()),
                        nvl(item.lastPrice()),
                        nvl(item.averagePurchasePrice()),
                        nvl(item.evaluationAmount()),
                        // 매입금액 = 평가금액 − 평가손익
                        nvl(item.evaluationAmount()).subtract(nvl(item.profitLossAmount()))))
                .toList();
    }

    /** 해외(미국) 주식. 원화 환산 금액을 나무가 직접 준다. */
    private List<BrokerageHolding> overseasHoldings() {
        NamuhGbBalanceResponse response;
        try {
            response = holdingsService.overseasBalance(null);
        } catch (Exception e) {
            // 해외 잔고가 없거나 조회에 실패해도 국내 보유분은 보여줘야 한다
            log.warn("나무증권 해외 잔고 조회 실패(국내 보유분만 사용): {}", e.getMessage());
            return List.of();
        }

        if (response.items() == null) {
            return List.of();
        }

        return response.items().stream()
                .filter(item -> item.symbol() != null)
                .filter(item -> nvl(item.quantity()).signum() > 0)
                .map(item -> new BrokerageHolding(
                        Broker.NAMUH,
                        item.symbol(),
                        item.name(),
                        "US",
                        item.currency() == null ? "USD" : item.currency(),
                        nvl(item.quantity()),
                        nvl(item.lastPriceForeign()),
                        nvl(item.averagePurchasePriceForeign()),
                        nvl(item.evaluationAmountKrw()),
                        nvl(item.purchaseAmountKrw())))
                .toList();
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
