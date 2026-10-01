package com.mystock.portfolio.brokerage;

import com.mystock.portfolio.external.toss.TossApiProperties;
import com.mystock.portfolio.service.TossPortfolioService;
import com.mystock.portfolio.service.TossPortfolioView;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 토스증권을 표준 모델로 감싸는 어댑터.
 *
 * 이미 잘 돌아가는 TossPortfolioService 를 그대로 쓰고, 결과만 표준 모델로 바꿔 담는다.
 * (환율 환산은 TossPortfolioService 안에서 이미 끝나 있다)
 */
@Component
public class TossBrokerageClient implements BrokerageClient {

    private final TossPortfolioService portfolioService;
    private final TossApiProperties properties;

    public TossBrokerageClient(TossPortfolioService portfolioService, TossApiProperties properties) {
        this.portfolioService = portfolioService;
        this.properties = properties;
    }

    @Override
    public Broker broker() {
        return Broker.TOSS;
    }

    @Override
    public boolean isConfigured() {
        return properties.clientId() != null && !properties.clientId().isBlank()
                && properties.clientSecret() != null && !properties.clientSecret().isBlank();
    }

    /**
     * @param ownerKey 무시한다. 토스는 API 키가 곧 신분이라 키 주인의 계좌만 조회되고,
     *                 다른 사람 계좌를 볼 방법이 애초에 없다.
     */
    @Override
    public List<BrokerageHolding> holdings(String ownerKey) {
        TossPortfolioView view = portfolioService.load();

        return view.items().stream()
                .map(item -> new BrokerageHolding(
                        Broker.TOSS,
                        item.symbol(),
                        item.name(),
                        item.marketCountry(),
                        item.currency(),
                        item.quantity(),
                        item.lastPrice(),
                        item.averagePurchasePrice(),
                        item.marketValueKrw(),
                        // 매입금액(원화) = 평가금액(원화) − 손익(원화)
                        item.marketValueKrw().subtract(item.profitLossKrw())))
                .toList();
    }
}
