package com.mystock.portfolio.service;

import com.mystock.portfolio.brokerage.Broker;
import com.mystock.portfolio.brokerage.BrokerageClient;
import com.mystock.portfolio.brokerage.BrokerageHolding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 여러 증권사의 보유 종목을 하나로 합쳐서 비중을 계산한다.
 *
 * ★★ 왜 합쳐야 하는가 (실제 사례) ★★
 * 지금 이 계좌들에는 SOXL 이 토스와 나무 양쪽에 들어있다.
 * 토스만 보면 SOXL 비중이 2.98% 지만, 나무의 SOXL 까지 합치면 전혀 다른 숫자가 된다.
 * 증권사별로 따로 계산한 비중을 믿고 리밸런싱하면 실제로는 엉뚱한 금액을 사고팔게 된다.
 *
 * ★ 증권사 목록을 생성자에서 List 로 받는다
 * 스프링이 BrokerageClient 를 구현한 빈을 전부 찾아 넣어준다.
 * 증권사를 추가해도 이 클래스는 고칠 필요가 없다.
 */
@Service
public class UnifiedPortfolioService {

    private static final Logger log = LoggerFactory.getLogger(UnifiedPortfolioService.class);

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** 전체 합산을 뜻하는 범위 값 */
    public static final String SCOPE_ALL = "ALL";

    private final List<BrokerageClient> brokerageClients;

    public UnifiedPortfolioService(List<BrokerageClient> brokerageClients) {
        this.brokerageClients = brokerageClients;
        log.info("연동된 증권사: {}", brokerageClients.stream().map(c -> c.broker().name()).toList());
    }

    /**
     * @param scope    "ALL" 이면 전체 합산, "TOSS"/"NAMUH"/"MANUAL" 이면 그 출처만
     * @param ownerKey 직접 입력한 종목이 누구 것인지 구분하는 값.
     *                 증권사 API 는 이 값을 무시한다(키가 곧 신분이므로).
     */
    public UnifiedPortfolioView load(String scope, String ownerKey) {
        return loadWithStatements(scope, ownerKey).view();
    }

    /**
     * 화면용 합산 결과 + 증권사별 원래 응답(합계 포함). 장 마감 스냅샷이 대사에 쓴다.
     * 증권사를 한 번만 부르고 둘 다 만든다.
     *
     * @param statements 조회에 성공한 증권사의 응답
     * @param failures   설정은 됐는데 조회에 실패한 증권사와 그 이유. 비어 있어야 스냅샷을 믿을 수 있다
     */
    public record Loaded(UnifiedPortfolioView view,
                         Map<Broker, com.mystock.portfolio.brokerage.BrokerageStatement> statements,
                         Map<Broker, String> failures) {
    }

    public Loaded loadWithStatements(String scope, String ownerKey) {
        String normalizedScope = normalizeScope(scope);
        Map<Broker, com.mystock.portfolio.brokerage.BrokerageStatement> statements = new LinkedHashMap<>();
        Map<Broker, String> failures = new LinkedHashMap<>();

        List<BrokerageHolding> allHoldings = new ArrayList<>();
        List<UnifiedPortfolioView.BrokerSummary> brokerSummaries = new ArrayList<>();

        // 증권사별로 하나씩 조회한다.
        // 한 곳이 실패해도 나머지는 보여줘야 하므로 개별로 try-catch 한다.
        for (BrokerageClient client : brokerageClients) {
            Broker broker = client.broker();

            boolean inScope = SCOPE_ALL.equals(normalizedScope) || broker.name().equals(normalizedScope);
            if (!inScope) {
                continue;
            }

            if (!client.isConfigured()) {
                brokerSummaries.add(summaryOf(broker, false, BigDecimal.ZERO, 0,
                        ".env 에 키가 설정되지 않았습니다."));
                continue;
            }

            try {
                com.mystock.portfolio.brokerage.BrokerageStatement statement = client.statement(ownerKey);
                statements.put(broker, statement);
                List<BrokerageHolding> holdings = statement.holdings();

                // 직접 입력분이 하나도 없으면 요약 칩에 "0원" 으로 끼어들 필요가 없다
                if (holdings.isEmpty() && broker == Broker.MANUAL) {
                    continue;
                }
                allHoldings.addAll(holdings);

                BigDecimal brokerValue = holdings.stream()
                        .map(BrokerageHolding::marketValueKrw)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                brokerSummaries.add(summaryOf(broker, true, brokerValue, holdings.size(), null));

            } catch (Exception e) {
                log.warn("{} 보유종목 조회 실패: {}", broker, e.getMessage());
                failures.put(broker, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                brokerSummaries.add(summaryOf(broker, true, BigDecimal.ZERO, 0, e.getMessage()));
            }
        }

        // 같은 종목을 증권사를 넘어 하나로 합친다
        Map<String, Merged> mergedBySymbol = new LinkedHashMap<>();
        for (BrokerageHolding holding : allHoldings) {
            mergedBySymbol.computeIfAbsent(holding.symbol(), key -> new Merged(holding)).add(holding);
        }

        BigDecimal totalValue = mergedBySymbol.values().stream()
                .map(m -> m.marketValueKrw)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPurchase = mergedBySymbol.values().stream()
                .map(m -> m.purchaseKrw)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<UnifiedPortfolioView.Item> items = mergedBySymbol.values().stream()
                .map(m -> m.toItem(totalValue))
                .sorted(Comparator.comparing(UnifiedPortfolioView.Item::marketValueKrw).reversed())
                .toList();

        // 증권사 비중도 전체 대비로 채워준다
        List<UnifiedPortfolioView.BrokerSummary> brokersWithWeight = brokerSummaries.stream()
                .map(b -> new UnifiedPortfolioView.BrokerSummary(
                        b.broker(), b.brokerName(), b.configured(), b.valueKrw(),
                        percentOf(b.valueKrw(), totalValue), b.itemCount(), b.error()))
                .toList();

        UnifiedPortfolioView view = new UnifiedPortfolioView(
                normalizedScope,
                totalValue,
                totalPurchase,
                totalValue.subtract(totalPurchase),
                percentOf(totalValue.subtract(totalPurchase), totalPurchase),
                brokersWithWeight,
                items);
        return new Loaded(view, statements, failures);
    }

    private String normalizeScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return SCOPE_ALL;
        }
        String upper = scope.trim().toUpperCase();
        if (SCOPE_ALL.equals(upper)) {
            return upper;
        }
        // 알 수 없는 값이면 전체로 처리한다 (오타로 빈 화면이 뜨는 것보다 낫다)
        boolean known = java.util.Arrays.stream(Broker.values()).anyMatch(b -> b.name().equals(upper));
        return known ? upper : SCOPE_ALL;
    }

    private UnifiedPortfolioView.BrokerSummary summaryOf(Broker broker, boolean configured,
                                                        BigDecimal value, int itemCount, String error) {
        return new UnifiedPortfolioView.BrokerSummary(
                broker.name(), broker.displayName(), configured, value, BigDecimal.ZERO, itemCount, error);
    }

    private static BigDecimal percentOf(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return part.multiply(HUNDRED).divide(whole, 2, RoundingMode.HALF_UP);
    }

    /**
     * 같은 종목을 여러 증권사에서 모아 담는 그릇.
     *
     * 수량과 금액은 더하고, 종목명·현재가 같은 "공통 정보" 는 처음 것을 쓴다.
     * (같은 종목이니 현재가는 어느 증권사에서 보든 같아야 한다)
     */
    private static final class Merged {

        private final String symbol;
        private final String name;
        private final String marketCountry;
        private final String currency;
        private final BigDecimal lastPrice;

        private BigDecimal quantity = BigDecimal.ZERO;
        private BigDecimal marketValueKrw = BigDecimal.ZERO;
        private BigDecimal purchaseKrw = BigDecimal.ZERO;
        private final List<UnifiedPortfolioView.Lot> lots = new ArrayList<>();

        /**
         * 평단가 가중평균을 내기 위한 누적값.
         * costSum = Σ(수량 × 평단가), costQuantity = 평단가를 아는 물량의 수량 합.
         * 마지막에 costSum ÷ costQuantity 로 나누면 가중평균 평단가가 나온다.
         * (단순 평균을 내면 100주 산 계좌와 1주 산 계좌가 같은 무게가 되어 틀린다)
         */
        private BigDecimal costSum = BigDecimal.ZERO;
        private BigDecimal costQuantity = BigDecimal.ZERO;

        private Merged(BrokerageHolding first) {
            this.symbol = first.symbol();
            this.name = first.name();
            this.marketCountry = first.marketCountry();
            this.currency = first.currency();
            this.lastPrice = first.lastPrice();
        }

        private void add(BrokerageHolding holding) {
            this.quantity = this.quantity.add(holding.quantity());
            this.marketValueKrw = this.marketValueKrw.add(holding.marketValueKrw());
            this.purchaseKrw = this.purchaseKrw.add(holding.purchaseAmountKrw());

            // 평단가를 모르는 증권사가 있으면 그 물량만 가중평균에서 빼고 계산한다
            BigDecimal avg = holding.averagePurchasePrice();
            if (avg != null && avg.signum() > 0 && holding.quantity().signum() > 0) {
                this.costSum = this.costSum.add(avg.multiply(holding.quantity()));
                this.costQuantity = this.costQuantity.add(holding.quantity());
            }

            this.lots.add(new UnifiedPortfolioView.Lot(
                    holding.broker().name(),
                    holding.broker().displayName(),
                    holding.quantity(),
                    holding.marketValueKrw()));
        }

        private UnifiedPortfolioView.Item toItem(BigDecimal totalValue) {
            BigDecimal profitLoss = marketValueKrw.subtract(purchaseKrw);

            // 평단가를 하나도 못 구했으면 null 로 둔다. 0 으로 채우면 "공짜로 샀다" 는 거짓말이 된다.
            BigDecimal averagePurchasePrice = costQuantity.signum() > 0
                    ? costSum.divide(costQuantity, 4, RoundingMode.HALF_UP)
                    : null;

            return new UnifiedPortfolioView.Item(
                    symbol, name, marketCountry, currency,
                    quantity, lastPrice, averagePurchasePrice,
                    marketValueKrw.setScale(0, RoundingMode.HALF_UP),
                    purchaseKrw.setScale(0, RoundingMode.HALF_UP),
                    profitLoss.setScale(0, RoundingMode.HALF_UP),
                    percentOf(profitLoss, purchaseKrw),
                    percentOf(marketValueKrw, totalValue),
                    List.copyOf(lots));
        }
    }
}
