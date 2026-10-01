package com.mystock.portfolio.service;

import com.mystock.portfolio.external.toss.TossAccountService;
import com.mystock.portfolio.external.toss.TossApiClient;
import com.mystock.portfolio.external.toss.TossMarketDataService;
import com.mystock.portfolio.external.toss.dto.TossApiEnvelope;
import com.mystock.portfolio.external.toss.dto.TossHoldingItem;
import com.mystock.portfolio.external.toss.dto.TossHoldings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 보유 주식을 가져와서 "화면에 바로 뿌릴 수 있는 모양" 으로 가공한다.
 *
 * 하는 일 순서:
 *   1. 어느 계좌를 쓸지 정한다
 *   2. 그 계좌의 보유주식을 가져온다
 *   3. 미국 주식이 있으면 환율을 가져와 원화로 환산한다
 *   4. 전체 합계를 내고, 종목별 비중(%)을 계산한다
 *
 * ★ 왜 환산이 필요한가
 * "애플이 내 자산의 몇 %인가" 를 알려면 원화 종목과 달러 종목을 같은 단위로 놓고 더해야 한다.
 * 토스는 통화별로 따로 합산해서 주기 때문에 이 계산은 우리가 직접 해야 한다.
 */
@Service
public class TossPortfolioService {

    private static final Logger log = LoggerFactory.getLogger(TossPortfolioService.class);

    private static final ParameterizedTypeReference<TossApiEnvelope<TossHoldings>> HOLDINGS_TYPE =
            new ParameterizedTypeReference<>() {
            };

    /** 퍼센트 계산용 상수 */
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final TossApiClient apiClient;
    private final TossAccountService accountService;
    private final TossMarketDataService marketDataService;

    public TossPortfolioService(TossApiClient apiClient,
                                TossAccountService accountService,
                                TossMarketDataService marketDataService) {
        this.apiClient = apiClient;
        this.accountService = accountService;
        this.marketDataService = marketDataService;
    }

    public TossPortfolioView load() {
        // 1. 계좌 결정
        Long accountSeq = accountService.resolveAccountSeq();

        // 2. 보유주식 조회 (이 API 는 X-Tossinvest-Account 헤더가 반드시 필요하다)
        TossHoldings holdings = apiClient.get(accountSeq, HOLDINGS_TYPE, "/api/v1/holdings");
        List<TossHoldingItem> rawItems = holdings.items() == null ? List.of() : holdings.items();

        // 3. 미국 주식이 하나라도 있을 때만 환율을 부른다 (불필요한 API 호출을 아낀다)
        boolean hasUsd = rawItems.stream().anyMatch(item -> isUsd(item.currency()));
        BigDecimal usdKrwRate = hasUsd ? marketDataService.usdToKrwRate() : null;

        // 4. 종목별로 원화 환산값을 먼저 계산해둔다 (비중을 내려면 전체 합계가 먼저 필요하기 때문)
        List<Converted> converted = new ArrayList<>();
        for (TossHoldingItem item : rawItems) {
            BigDecimal rate = isUsd(item.currency()) ? usdKrwRate : BigDecimal.ONE;
            converted.add(convert(item, rate));
        }

        BigDecimal totalValueKrw = sum(converted, c -> c.marketValueKrw);
        BigDecimal totalPurchaseKrw = sum(converted, c -> c.purchaseKrw);
        BigDecimal totalProfitLossKrw = totalValueKrw.subtract(totalPurchaseKrw);

        // 5. 비중 계산 후 화면용 객체로 변환
        List<TossPortfolioView.Item> viewItems = new ArrayList<>();
        for (Converted c : converted) {
            viewItems.add(new TossPortfolioView.Item(
                    c.source.symbol(),
                    c.source.name(),
                    c.source.marketCountry(),
                    c.source.currency(),
                    nvl(c.source.quantity()),
                    nvl(c.source.lastPrice()),
                    nvl(c.source.averagePurchasePrice()),
                    nvl(marketValueOf(c.source)),
                    c.marketValueKrw,
                    c.profitLossKrw,
                    c.profitRatePercent,
                    percentOf(c.marketValueKrw, totalValueKrw)
            ));
        }

        // 비중이 큰 종목이 위로 오게 정렬하면 표가 훨씬 읽기 좋다
        viewItems.sort((a, b) -> b.marketValueKrw().compareTo(a.marketValueKrw()));

        log.info("포트폴리오 조회 완료. 종목 수={}, 총 평가금액={}원", viewItems.size(), totalValueKrw);

        return new TossPortfolioView(
                accountSeq,
                totalValueKrw,
                totalPurchaseKrw,
                totalProfitLossKrw,
                percentOf(totalProfitLossKrw, totalPurchaseKrw),
                usdKrwRate,
                viewItems
        );
    }

    /** 한 종목의 원화 환산값들을 미리 계산해서 담아두는 임시 그릇 */
    private Converted convert(TossHoldingItem item, BigDecimal rate) {
        BigDecimal marketValue = nvl(marketValueOf(item));
        BigDecimal purchase = nvl(purchaseAmountOf(item));
        BigDecimal profitLoss = nvl(profitAmountOf(item));

        return new Converted(
                item,
                toKrw(marketValue, rate),
                toKrw(purchase, rate),
                toKrw(profitLoss, rate),
                // 수익률은 통화와 무관한 비율이므로 환산하지 않는다. 소수 → 퍼센트로만 바꾼다.
                nvl(profitRateOf(item)).multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP)
        );
    }

    /** 원화로 환산한다. 원화는 소수점을 버리고 정수로 맞춘다. */
    private BigDecimal toKrw(BigDecimal amount, BigDecimal rate) {
        return amount.multiply(rate).setScale(0, RoundingMode.HALF_UP);
    }

    /** part 가 whole 의 몇 퍼센트인지. whole 이 0 이면 0 을 돌려준다(0으로 나누기 방지). */
    private BigDecimal percentOf(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return part.multiply(HUNDRED).divide(whole, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal sum(List<Converted> list, java.util.function.Function<Converted, BigDecimal> picker) {
        return list.stream()
                .map(picker)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean isUsd(String currency) {
        return "USD".equalsIgnoreCase(currency);
    }

    /** null 이면 0 으로 바꿔준다. 토스가 특정 필드를 안 줄 때 NullPointerException 이 나지 않게 한다. */
    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    // 아래 세 메서드는 "중첩된 객체를 안전하게 꺼내기" 용도다.
    // item.marketValue() 자체가 null 일 수 있으므로 한 단계씩 확인한다.

    private static BigDecimal marketValueOf(TossHoldingItem item) {
        return item.marketValue() == null ? null : item.marketValue().amount();
    }

    private static BigDecimal purchaseAmountOf(TossHoldingItem item) {
        return item.marketValue() == null ? null : item.marketValue().purchaseAmount();
    }

    private static BigDecimal profitAmountOf(TossHoldingItem item) {
        return item.profitLoss() == null ? null : item.profitLoss().amount();
    }

    private static BigDecimal profitRateOf(TossHoldingItem item) {
        return item.profitLoss() == null ? null : item.profitLoss().rate();
    }

    /** 계산 중간 결과를 담는 내부 전용 그릇 */
    private record Converted(
            TossHoldingItem source,
            BigDecimal marketValueKrw,
            BigDecimal purchaseKrw,
            BigDecimal profitLossKrw,
            BigDecimal profitRatePercent
    ) {
    }
}
