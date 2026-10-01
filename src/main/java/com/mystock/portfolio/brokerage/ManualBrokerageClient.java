package com.mystock.portfolio.brokerage;

import com.mystock.portfolio.common.AppException;
import com.mystock.portfolio.domain.ManualHolding;
import com.mystock.portfolio.domain.ManualHoldingRepository;
import com.mystock.portfolio.external.toss.TossMarketDataService;
import com.mystock.portfolio.external.toss.dto.TossPrice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 직접 입력한 보유종목을 증권사와 똑같은 모양으로 내어준다.
 *
 * ★ 이 클래스 덕분에 API 키가 없는 사람도 앱을 그대로 쓸 수 있다
 * 비중 계산, 변동성, 차트, 기업분석 코드는 이게 어디서 온 종목인지 모른다.
 * 토스에서 왔든 직접 입력했든 똑같이 처리된다.
 *
 * ★ 현재가는 앱이 채워준다
 * 사용자는 종목·수량·평단가만 알려주면 된다.
 * 시세는 계좌와 무관한 공개 데이터라 앱의 토스 키 하나로 어떤 종목이든 조회된다.
 * 그래서 "직접 입력" 이지만 평가금액과 수익률은 실시간으로 계산된다.
 */
@Component
public class ManualBrokerageClient implements BrokerageClient {

    private static final Logger log = LoggerFactory.getLogger(ManualBrokerageClient.class);

    private final ManualHoldingRepository repository;
    private final TossMarketDataService marketDataService;

    public ManualBrokerageClient(ManualHoldingRepository repository,
                                 TossMarketDataService marketDataService) {
        this.repository = repository;
        this.marketDataService = marketDataService;
    }

    @Override
    public Broker broker() {
        return Broker.MANUAL;
    }

    /** 직접 입력은 API 키가 필요 없으므로 항상 사용 가능하다 */
    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BrokerageHolding> holdings(String ownerKey) {
        if (ownerKey == null || ownerKey.isBlank()) {
            return List.of();
        }

        List<ManualHolding> saved = repository.findByOwnerKey(ownerKey);
        if (saved.isEmpty()) {
            return List.of();
        }

        // 현재가를 한 번에 조회한다 (종목마다 따로 부르면 호출 한도에 걸린다)
        Map<String, TossPrice> prices = fetchPrices(saved);

        // 달러 종목이 있으면 환율이 반드시 필요하다
        boolean hasUsd = saved.stream().anyMatch(h -> "USD".equalsIgnoreCase(h.getCurrency()));
        BigDecimal usdKrwRate = hasUsd ? fetchUsdRate() : BigDecimal.ONE;

        List<BrokerageHolding> result = new ArrayList<>();
        for (ManualHolding holding : saved) {
            TossPrice price = prices.get(holding.getSymbol());

            // 시세를 못 구하면 평단가를 현재가 대신 쓴다.
            // 그래야 평가금액이 0원이 되어 비중 계산이 망가지는 일을 막는다.
            BigDecimal lastPrice = (price != null && price.lastPrice() != null && price.lastPrice().signum() > 0)
                    ? price.lastPrice()
                    : holding.getAveragePurchasePrice();

            BigDecimal rate = "USD".equalsIgnoreCase(holding.getCurrency()) ? usdKrwRate : BigDecimal.ONE;

            BigDecimal marketValueKrw = lastPrice.multiply(holding.getQuantity())
                    .multiply(rate).setScale(0, RoundingMode.HALF_UP);
            BigDecimal purchaseKrw = holding.getAveragePurchasePrice().multiply(holding.getQuantity())
                    .multiply(rate).setScale(0, RoundingMode.HALF_UP);

            result.add(new BrokerageHolding(
                    Broker.MANUAL,
                    holding.getSymbol(),
                    holding.getName(),
                    holding.getMarketCountry(),
                    holding.getCurrency(),
                    holding.getQuantity(),
                    lastPrice,
                    holding.getAveragePurchasePrice(),
                    marketValueKrw,
                    purchaseKrw));
        }
        return result;
    }

    /** 보유 종목들의 현재가를 한 번에 조회한다. 실패해도 분석을 막지 않는다. */
    private Map<String, TossPrice> fetchPrices(List<ManualHolding> holdings) {
        Map<String, TossPrice> map = new LinkedHashMap<>();
        try {
            List<String> symbols = holdings.stream().map(ManualHolding::getSymbol).distinct().toList();
            for (TossPrice price : marketDataService.prices(symbols)) {
                map.put(price.symbol(), price);
            }
        } catch (Exception e) {
            // 시세를 못 가져와도 평단가 기준으로는 보여줄 수 있다
            log.warn("직접 입력 종목의 현재가 조회 실패(평단가로 대체): {}", e.getMessage());
        }
        return map;
    }

    /**
     * 환율을 가져온다.
     *
     * ★ 실패하면 계산을 포기한다. 1:1 로 대충 넘기지 않는다.
     * 예전에 실패 시 1 을 돌려줬더니 $136 짜리 종목이 136원으로 표시됐다.
     * 1,380배 틀린 숫자가 비중 계산까지 망가뜨려서, 틀린 값을 보여주느니
     * 왜 못 보여주는지 말하는 편이 낫다.
     *
     * 이 예외는 UnifiedPortfolioService 가 받아서 "직접 입력" 칸에 이유로 표시한다.
     * 원화 종목만 가진 사람은 애초에 이 메서드를 타지 않으므로 영향이 없다.
     */
    private BigDecimal fetchUsdRate() {
        try {
            return marketDataService.usdToKrwRate();
        } catch (Exception e) {
            throw new AppException(
                    "환율을 가져오지 못해 달러 종목을 원화로 바꿀 수 없습니다. "
                            + "토스증권 연결이 정상인지 확인해 주세요. (원인: " + e.getMessage() + ")", e);
        }
    }
}
