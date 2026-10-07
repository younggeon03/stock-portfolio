package com.mystock.portfolio.service.snapshot;

import com.mystock.portfolio.brokerage.Broker;
import com.mystock.portfolio.brokerage.BrokerageClient;
import com.mystock.portfolio.brokerage.BrokerageHolding;
import com.mystock.portfolio.brokerage.BrokerageStatement;
import com.mystock.portfolio.service.UnifiedPortfolioService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 장 마감 스냅샷: 언제 찍고, 언제 안 찍고, 대사를 어떻게 세는지. 금액은 데모 값 */
class PortfolioSnapshotServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 7);
    private static final Clock CLOCK = Clock.fixed(
            ZonedDateTime.of(2026, 10, 7, 16, 10, 0, 0, ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));

    private final PortfolioSnapshotStore store = mock(PortfolioSnapshotStore.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    /** 테스트용 증권사. 합계를 함께 돌려주거나, 조회에 실패한다 */
    private static BrokerageClient broker(Broker which, BigDecimal value, BigDecimal reportedTotal, boolean fail) {
        return new BrokerageClient() {
            public Broker broker() { return which; }
            public boolean isConfigured() { return true; }
            public List<BrokerageHolding> holdings(String ownerKey) { return statement(ownerKey).holdings(); }
            public BrokerageStatement statement(String ownerKey) {
                if (fail) throw new IllegalStateException("403 허용 IP");
                BrokerageHolding h = new BrokerageHolding(which, "005930", "데모전자", "KR", "KRW",
                        BigDecimal.TEN, new BigDecimal("1000"), new BigDecimal("900"), value, new BigDecimal("9000"));
                return new BrokerageStatement(List.of(h),
                        List.of(new BrokerageStatement.ReportedTotal("국내", "KRW", reportedTotal, value)));
            }
        };
    }

    private PortfolioSnapshotService service(BrokerageClient... clients) {
        return new PortfolioSnapshotService(new UnifiedPortfolioService(List.of(clients)), store, registry, "", CLOCK);
    }

    private double runs(String outcome) {
        var c = registry.find("portfolio.snapshot.runs").tag("outcome", outcome).counter();
        return c == null ? 0 : c.count();
    }

    @Test
    void 증권사_합계와_맞으면_저장하고_불일치_0() {
        var result = service(broker(Broker.NAMUH, new BigDecimal("10000"), new BigDecimal("10000"), false)).take(false);

        assertThat(result.outcome()).isEqualTo(PortfolioSnapshotService.Outcome.SUCCESS);
        assertThat(result.date()).isEqualTo(TODAY);   // 서울 날짜
        assertThat(result.mismatches()).isZero();
        verify(store).save(eq(TODAY), any(), any(), anyList());
        assertThat(runs("success")).isEqualTo(1);
        assertThat(registry.find("portfolio.reconciliation.mismatches").gauge().value()).isZero();
    }

    @Test
    void 합계가_어긋나면_저장은_하고_불일치를_센다() {
        // 앱 10,000 vs 증권사 12,000 → 허용 오차(12원)를 넘는다
        var result = service(broker(Broker.NAMUH, new BigDecimal("10000"), new BigDecimal("12000"), false)).take(false);

        assertThat(result.outcome()).isEqualTo(PortfolioSnapshotService.Outcome.SUCCESS);
        assertThat(result.mismatches()).isEqualTo(1);
        assertThat(registry.find("portfolio.reconciliation.mismatches").gauge().value()).isEqualTo(1);
    }

    @Test
    void 증권사_하나라도_실패하면_저장하지_않는다() {
        // 토스 몫이 빠진 합계가 이력에 들어가면 그래프에 하루짜리 절벽이 생긴다
        var result = service(
                broker(Broker.NAMUH, new BigDecimal("10000"), new BigDecimal("10000"), false),
                broker(Broker.TOSS, BigDecimal.ZERO, BigDecimal.ZERO, true)).take(false);

        assertThat(result.outcome()).isEqualTo(PortfolioSnapshotService.Outcome.FAILURE);
        assertThat(result.reason()).contains("TOSS");
        verify(store, never()).save(any(), any(), any(), anyList());
        assertThat(runs("failure")).isEqualTo(1);
    }

    @Test
    void 재시도는_오늘_것이_있으면_건너뛰고_수동_실행은_다시_찍는다() {
        when(store.exists(TODAY)).thenReturn(true);
        var s = service(broker(Broker.NAMUH, new BigDecimal("10000"), new BigDecimal("10000"), false));

        assertThat(s.take(false).outcome()).isEqualTo(PortfolioSnapshotService.Outcome.SKIPPED);
        verify(store, never()).save(any(), any(), any(), anyList());

        assertThat(s.take(true).outcome()).isEqualTo(PortfolioSnapshotService.Outcome.SUCCESS);
        verify(store).save(eq(TODAY), any(), any(), anyList());
    }

    @Test
    void 연결된_증권사가_없으면_조용히_건너뛴다() {
        BrokerageClient off = new BrokerageClient() {
            public Broker broker() { return Broker.TOSS; }
            public boolean isConfigured() { return false; }
            public List<BrokerageHolding> holdings(String ownerKey) { return List.of(); }
        };

        assertThat(service(off).take(false).outcome()).isEqualTo(PortfolioSnapshotService.Outcome.SKIPPED);
        assertThat(runs("failure")).isZero();
    }
}
