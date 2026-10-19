package com.mystock.portfolio.service.tax;

import com.mystock.portfolio.brokerage.Broker;
import com.mystock.portfolio.external.namuh.NamuhAccountService;
import com.mystock.portfolio.external.namuh.NamuhApiClient;
import com.mystock.portfolio.external.namuh.NamuhApiProperties;
import com.mystock.portfolio.external.namuh.dto.NamuhPeriodPnlResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 나무의 해외주식 실현손익. 나무가 계산해 준 원화 기준 기간손익을 그대로 쓴다.
 *
 * ★ 주문일 기준이다
 * 나무 API 는 기간을 주문일로 받는다. 세법은 결제일 기준이라 12월 말 마지막 거래일 즈음 판 것은
 * 해가 갈릴 수 있다. 화면에 그 점을 적는다.
 */
@Component
public class NamuhForeignGainSource implements ForeignGainSource {

    private static final String PATH = "/gbstock/inquiry/v1/periodPnl";
    private static final DateTimeFormatter YMD = DateTimeFormatter.BASIC_ISO_DATE;

    private final NamuhApiClient apiClient;
    private final NamuhAccountService accountService;
    private final NamuhApiProperties properties;

    public NamuhForeignGainSource(NamuhApiClient apiClient, NamuhAccountService accountService,
                                  NamuhApiProperties properties) {
        this.apiClient = apiClient;
        this.accountService = accountService;
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

    @Override
    public BrokerGains realized(LocalDate from, LocalDate to) {
        NamuhPeriodPnlResponse r = apiClient.post(PATH,
                NamuhPeriodPnlResponse.Request.krw(accountService.resolveAccountNo(), from.format(YMD), to.format(YMD)),
                NamuhPeriodPnlResponse.class);
        return toGains(r);
    }

    static BrokerGains toGains(NamuhPeriodPnlResponse r) {
        if (r.summary() == null) {
            return new BrokerGains(BigDecimal.ZERO, BigDecimal.ZERO, List.of(), List.of());
        }
        List<Sale> sales = r.daily() == null ? List.of() : r.daily().stream()
                .filter(d -> d.sellQuantity() != null && d.sellQuantity().signum() > 0)
                .map(d -> new Sale(null, LocalDate.parse(d.orderDate(), YMD), d.sellQuantity(),
                        nz(d.sellAmount()), nz(d.realized())))
                .toList();
        return new BrokerGains(nz(r.summary().realizedSum()), nz(r.summary().sellAmountSum()), sales,
                List.of("나무증권 계산값(주문일 기준, 취득가 방식은 나무에서 고른 방식)"));
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
