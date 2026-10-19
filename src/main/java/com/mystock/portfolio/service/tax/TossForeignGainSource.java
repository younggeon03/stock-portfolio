package com.mystock.portfolio.service.tax;

import com.mystock.portfolio.brokerage.Broker;
import com.mystock.portfolio.external.toss.TossAccountService;
import com.mystock.portfolio.external.toss.TossApiClient;
import com.mystock.portfolio.external.toss.TossApiProperties;
import com.mystock.portfolio.external.toss.dto.TossApiEnvelope;
import com.mystock.portfolio.external.toss.dto.TossExchangeRate;
import com.mystock.portfolio.external.toss.dto.TossOrderPage;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 토스의 해외주식 실현손익. 토스는 손익을 계산해 주지 않아서 끝난 주문 전체를 받아 앱이 계산한다.
 *
 * ★ 왜 올해 주문만이 아니라 전체를 받나
 * 올해 판 주식이 작년에 산 것일 수 있다. 선입선출로 취득가를 찾으려면 그 매수 기록까지 있어야 한다.
 *
 * ★ 확인 못 한 가정 (토스 허용 IP 가 막혀 실제 호출로 못 봤다. 공개 스펙 사본에 형식이 빠져 있었다)
 *   - from/to 를 비우면 전체 기간이 온다
 *   - 환율 API 의 dateTime 에 과거 시각을 주면 그때 환율이 온다. midRate 를 기준환율로 쓴다
 * 틀리면 예외가 나서 화면에 "토스 계산 실패" 로 뜨고, 나무 쪽 결과는 그대로 나온다.
 */
@Component
public class TossForeignGainSource implements ForeignGainSource {

    private static final ParameterizedTypeReference<TossApiEnvelope<TossOrderPage>> ORDERS_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<TossApiEnvelope<TossExchangeRate>> RATE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    /** 한 번에 받을 수 있는 최대치. 페이지가 수백 장이면 뭔가 잘못된 것이라 끊는다 */
    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 200;

    private final TossApiClient apiClient;
    private final TossAccountService accountService;
    private final TossApiProperties properties;

    public TossForeignGainSource(TossApiClient apiClient, TossAccountService accountService,
                                 TossApiProperties properties) {
        this.apiClient = apiClient;
        this.accountService = accountService;
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

    @Override
    public BrokerGains realized(LocalDate from, LocalDate to) {
        Long account = accountService.resolveAccountSeq();
        List<FifoGainCalculator.Fill> fills = new ArrayList<>();

        String cursor = null;
        for (int page = 0; page < MAX_PAGES; page++) {
            TossOrderPage result = cursor == null
                    ? apiClient.get(account, ORDERS_TYPE, "/api/v1/orders?status=CLOSED&limit={limit}", PAGE_SIZE)
                    : apiClient.get(account, ORDERS_TYPE, "/api/v1/orders?status=CLOSED&limit={limit}&cursor={cursor}",
                    PAGE_SIZE, cursor);
            for (TossOrderPage.Order o : result.orders()) {
                FifoGainCalculator.Fill fill = toFill(o);
                if (fill != null) fills.add(fill);
            }
            if (!result.hasNext() || result.nextCursor() == null) break;
            cursor = result.nextCursor();
        }

        Map<LocalDate, BigDecimal> rates = new HashMap<>();
        return FifoGainCalculator.calculate(fills, from, to, date -> rates.computeIfAbsent(date, this::baseRate));
    }

    /** 미국 주식이고 실제로 체결된 것만. 취소·거부된 주문은 체결 수량이 0 이다 */
    static FifoGainCalculator.Fill toFill(TossOrderPage.Order o) {
        TossOrderPage.Execution e = o.execution();
        if (!"USD".equals(o.currency()) || e == null || e.filledQuantity() == null
                || e.filledQuantity().signum() <= 0 || e.filledAmount() == null) {
            return null;
        }
        // 결제 전(오늘 체결)이면 결제일이 비어 있다. 체결일로 대신하되 연말엔 하루 이틀 어긋날 수 있다
        LocalDate settle = e.settlementDate() != null ? e.settlementDate()
                : LocalDate.parse(e.filledAt().substring(0, 10));
        return new FifoGainCalculator.Fill(o.symbol(), "BUY".equals(o.side()), e.filledQuantity(), e.filledAmount(),
                e.commission(), e.tax(), settle, e.filledAt());
    }

    /** 그날 매매기준율. 기준환율은 오전에 고시되니 정오 시각으로 묻는다 */
    private BigDecimal baseRate(LocalDate date) {
        TossExchangeRate r = apiClient.get(null, RATE_TYPE,
                "/api/v1/exchange-rate?baseCurrency=USD&quoteCurrency=KRW&dateTime={dt}",
                date + "T12:00:00+09:00");
        BigDecimal rate = r.midRate() != null ? r.midRate() : r.rate();
        if (rate == null || rate.signum() <= 0) {
            throw new IllegalStateException(date + " 환율이 0 이하로 내려왔습니다");
        }
        return rate;
    }
}
