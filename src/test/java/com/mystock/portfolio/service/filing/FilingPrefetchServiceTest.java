package com.mystock.portfolio.service.filing;

import com.mystock.portfolio.brokerage.Broker;
import com.mystock.portfolio.brokerage.BrokerageClient;
import com.mystock.portfolio.brokerage.BrokerageHolding;
import com.mystock.portfolio.domain.CompanyAnalysis;
import com.mystock.portfolio.domain.CompanyAnalysisRepository;
import com.mystock.portfolio.external.filing.FilingService;
import com.mystock.portfolio.external.filing.FilingService.Prefetch;
import com.mystock.portfolio.external.toss.TossMarketDataService;
import com.mystock.portfolio.external.toss.dto.TossStockInfo;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 공시 재무 야간 배치: 어떤 종목을, 언제 건너뛰고, 토스가 막히면 어떻게 멈추는지. 종목·수량은 데모 값 */
class FilingPrefetchServiceTest {

    private final CompanyAnalysisRepository analyses = mock(CompanyAnalysisRepository.class);
    private final TossMarketDataService toss = mock(TossMarketDataService.class);
    private final FilingService filing = mock(FilingService.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private static BrokerageHolding holding(Broker broker, String symbol, String country) {
        return new BrokerageHolding(broker, symbol, "데모" + symbol, country, "KR".equals(country) ? "KRW" : "USD",
                BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN);
    }

    private static BrokerageClient broker(Broker which, boolean fail, BrokerageHolding... holdings) {
        return new BrokerageClient() {
            public Broker broker() { return which; }
            public boolean isConfigured() { return true; }
            public List<BrokerageHolding> holdings(String ownerKey) {
                if (fail) throw new IllegalStateException("403 허용 IP");
                return List.of(holdings);
            }
        };
    }

    private static TossStockInfo info(BigDecimal shares, boolean fund) {
        TossStockInfo info = mock(TossStockInfo.class);
        when(info.sharesOutstanding()).thenReturn(shares);
        when(info.isFund()).thenReturn(fund);
        return info;
    }

    private FilingPrefetchService service(BrokerageClient... clients) {
        return new FilingPrefetchService(List.of(clients), analyses, toss, filing, registry, "", 0);
    }

    private double count(String name, String tag, String value) {
        var c = registry.find(name).tag(tag, value).counter();
        return c == null ? 0 : c.count();
    }

    @Test
    void 보유_종목이_먼저_분석해_둔_종목이_뒤에_같은_종목은_한_번만() {
        when(analyses.findAll()).thenReturn(List.of(new CompanyAnalysis("avgo", "데모"), new CompanyAnalysis("MSFT", "데모")));
        FilingPrefetchService s = service(
                broker(Broker.TOSS, false, holding(Broker.TOSS, "005930", "KR"), holding(Broker.TOSS, "AVGO", "US")),
                broker(Broker.NAMUH, false, holding(Broker.NAMUH, "005930", "KR")));

        Map<String, String> targets = s.targets();

        assertThat(new ArrayList<>(targets.keySet())).containsExactly("005930", "AVGO", "MSFT");
        assertThat(targets).containsEntry("005930", "KR").containsEntry("MSFT", "US");
    }

    @Test
    void 증권사_하나가_실패해도_나머지와_분석_종목은_받는다() {
        when(analyses.findAll()).thenReturn(List.of(new CompanyAnalysis("MSFT", "데모")));
        FilingPrefetchService s = service(
                broker(Broker.TOSS, true),
                broker(Broker.NAMUH, false, holding(Broker.NAMUH, "000660", "KR")));

        assertThat(s.targets().keySet()).containsExactly("000660", "MSFT");
    }

    @Test
    void 종목마다_주식수를_넘겨_다시_받고_결과를_센다() {
        when(analyses.findAll()).thenReturn(List.of(new CompanyAnalysis("MSFT", "데모"), new CompanyAnalysis("SPY", "데모")));
        TossStockInfo msft = info(new BigDecimal("7400000000"), false);
        TossStockInfo spy = info(null, true);
        when(toss.stockInfo("MSFT")).thenReturn(msft);
        when(toss.stockInfo("SPY")).thenReturn(spy);
        when(filing.refresh("MSFT", "US", false, new BigDecimal("7400000000"))).thenReturn(Prefetch.REFRESHED);
        when(filing.refresh("SPY", "US", true, null)).thenReturn(Prefetch.NOT_APPLICABLE);

        FilingPrefetchService.Result r = service().run();

        assertThat(r.outcome()).isEqualTo(FilingPrefetchService.Outcome.SUCCESS);
        assertThat(r.targets()).isEqualTo(2);
        assertThat(r.counts()).containsEntry("REFRESHED", 1).containsEntry("NOT_APPLICABLE", 1);
        assertThat(count("filing.prefetch.symbols", "result", "refreshed")).isEqualTo(1);
        assertThat(count("filing.prefetch.runs", "outcome", "success")).isEqualTo(1);
    }

    @Test
    void 국내_종목인데_주식수를_못_받으면_저장본을_덮어쓰지_않게_건너뛴다() {
        when(analyses.findAll()).thenReturn(List.of());
        TossStockInfo noShares = info(null, false);
        when(toss.stockInfo("005930")).thenReturn(noShares);
        when(toss.stockInfo("AAPL")).thenReturn(null);   // 토스에 없는 종목

        FilingPrefetchService.Result r = service(broker(Broker.TOSS, false,
                holding(Broker.TOSS, "005930", "KR"), holding(Broker.TOSS, "AAPL", "US"))).run();

        assertThat(r.counts()).containsEntry(FilingPrefetchService.SKIPPED, 2);
        verify(filing, never()).refresh(anyString(), anyString(), anyBoolean(), any());
    }

    @Test
    void 토스가_막히면_남은_종목을_부르지_않고_실패로_남긴다() {
        when(analyses.findAll()).thenReturn(List.of(new CompanyAnalysis("MSFT", "데모"), new CompanyAnalysis("AVGO", "데모")));
        when(toss.stockInfo("MSFT")).thenThrow(new IllegalStateException("토스 403 허용 IP"));

        FilingPrefetchService.Result r = service().run();

        assertThat(r.outcome()).isEqualTo(FilingPrefetchService.Outcome.FAILURE);
        assertThat(r.reason()).contains("MSFT");
        verify(toss, never()).stockInfo("AVGO");
        verify(filing, never()).refresh(anyString(), anyString(), anyBoolean(), any());
        assertThat(count("filing.prefetch.runs", "outcome", "failure")).isEqualTo(1);
    }

    @Test
    void 받을_종목이_없으면_아무것도_부르지_않고_성공() {
        when(analyses.findAll()).thenReturn(List.of());

        FilingPrefetchService.Result r = service().run();

        assertThat(r.outcome()).isEqualTo(FilingPrefetchService.Outcome.SUCCESS);
        assertThat(r.targets()).isZero();
        verifyNoInteractions(toss, filing);
    }
}
