package com.mystock.portfolio.service.snapshot;

import com.mystock.portfolio.common.LogContext;
import com.mystock.portfolio.service.UnifiedPortfolioService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 장 마감 뒤 보유 현황 스냅샷 + 증권사 합계 대사. 하루 한 벌.
 *
 * ★ 언제 (한국 시간, 평일)
 * 16:10 에 찍는다. 국내 장(15:30)이 끝난 뒤라 국내는 오늘 종가, 미국은 어젯밤 종가, 환율은 그 시각 값이다.
 * 하루 한 시각으로 정해 두면 "날마다 같은 기준" 이 된다. 미국 마감(새벽 5~6시) 뒤에 따로 찍지 않는 이유는
 * 두 번 찍으면 하루에 두 줄이 생겨 기준이 섞이기 때문이다.
 * 18:10·20:10 에 한 번씩 더 돈다. 그날 이미 성공했으면 건너뛰고, 실패했으면 다시 시도한다.
 * 과거 날짜는 나중에 만들 수 없다(증권사는 지금 잔고만 준다). 그래서 재시도를 시간표에 넣었다.
 * 휴장일에도 돈다. 값이 앞날과 같을 뿐이라 해롭지 않고, 휴장일 달력을 관리하지 않아도 된다.
 *
 * ★ 언제 저장하지 않나
 * 설정된 증권사 중 하나라도 조회에 실패하면 저장하지 않는다. 그 증권사 몫이 빠진 합계가 이력에 들어가면
 * 그래프에 하루짜리 절벽이 생기고, 수익률 계산이 틀어진다. 빈 날로 두고 재시도에 맡긴다.
 * 연결된 증권사가 하나도 없으면(내 PC 에서 키 없이) 조용히 건너뛴다.
 *
 * ★ 알림은 지표로
 * 실행 결과(portfolio_snapshot_runs_total{outcome})와 마지막 대사 불일치 수(portfolio_reconciliation_mismatches)를
 * 내보내고, Prometheus 규칙이 텔레그램으로 알린다(alerts.yml). 앱이 직접 메시지를 보내지 않는다 —
 * 알림 경로를 한 곳(Alertmanager)으로 모아야 묶기·조용히 하기·재알림이 일관된다.
 */
@Service
public class PortfolioSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioSnapshotService.class);
    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    /** 한 번 실행의 결과 */
    public enum Outcome { SUCCESS, SKIPPED, FAILURE }

    /**
     * @param mismatches 대사에서 어긋난 줄 수 (성공했을 때만 뜻이 있다)
     * @param reason     건너뛰거나 실패한 이유
     */
    public record Result(Outcome outcome, LocalDate date, int mismatches, String reason) {
    }

    private final UnifiedPortfolioService portfolioService;
    private final PortfolioSnapshotStore store;
    private final Clock clock;
    private final String ownerKey;
    private final MeterRegistry registry;
    private final AtomicInteger lastMismatches = new AtomicInteger();

    // 생성자가 둘(테스트용 시계를 받는 것)이라 스프링이 쓸 쪽을 밝혀야 한다. 빼면 기동이 멈춘다(실제로 겪음)
    @org.springframework.beans.factory.annotation.Autowired
    public PortfolioSnapshotService(UnifiedPortfolioService portfolioService, PortfolioSnapshotStore store,
                                    MeterRegistry registry,
                                    @Value("${snapshot.owner-key:}") String ownerKey) {
        this(portfolioService, store, registry, ownerKey, Clock.system(SEOUL));
    }

    PortfolioSnapshotService(UnifiedPortfolioService portfolioService, PortfolioSnapshotStore store,
                             MeterRegistry registry, String ownerKey, Clock clock) {
        this.portfolioService = portfolioService;
        this.store = store;
        this.registry = registry;
        this.ownerKey = ownerKey == null || ownerKey.isBlank() ? null : ownerKey.trim();
        this.clock = clock;
        Gauge.builder("portfolio.reconciliation.mismatches", lastMismatches, AtomicInteger::get)
                .description("마지막 스냅샷에서 증권사 합계와 어긋난 줄 수")
                .register(registry);
    }

    /** 평일 16:10, 18:10, 20:10. 뒤의 두 번은 그날 성공했으면 건너뛰는 재시도다 */
    @Scheduled(cron = "${snapshot.cron:0 10 16,18,20 * * MON-FRI}", zone = "Asia/Seoul")
    public void scheduled() {
        LogContext.job("batch-snapshot", () -> take(false)).run();
    }

    /**
     * 오늘 스냅샷을 찍는다.
     *
     * @param force true 면 오늘 것이 이미 있어도 다시 찍는다(수동 실행). false 면 있으면 건너뛴다(재시도)
     */
    public synchronized Result take(boolean force) {
        LocalDate today = LocalDate.now(clock);
        if (!force && store.exists(today)) {
            return record(new Result(Outcome.SKIPPED, today, 0, "오늘 스냅샷이 이미 있습니다"));
        }
        try {
            UnifiedPortfolioService.Loaded loaded = portfolioService.loadWithStatements(UnifiedPortfolioService.SCOPE_ALL, ownerKey);
            if (!loaded.failures().isEmpty()) {
                return record(new Result(Outcome.FAILURE, today, 0, "조회 실패: " + loaded.failures()));
            }
            if (loaded.statements().isEmpty() || loaded.view().items().isEmpty()) {
                return record(new Result(Outcome.SKIPPED, today, 0, "연결된 증권사나 보유 종목이 없습니다"));
            }

            List<Reconciler.Line> lines = Reconciler.reconcile(loaded.statements());
            store.save(today, LocalDateTime.now(clock), loaded.view(), lines);
            int mismatches = (int) lines.stream().filter(l -> !l.matched()).count();
            lastMismatches.set(mismatches);
            // 금액은 로그에 남기지 않는다. 서버 로그에 잔고가 쌓이면 안 된다(공개 저장소 규칙과 같은 생각)
            lines.stream().filter(l -> !l.matched()).forEach(l ->
                    log.warn("대사 불일치: {} {} ({}) — 앱 합계와 증권사 합계가 허용 오차를 넘게 다릅니다",
                            l.broker(), l.scope(), l.currency()));
            return record(new Result(Outcome.SUCCESS, today, mismatches, null));
        } catch (Exception e) {
            return record(new Result(Outcome.FAILURE, today, 0, e.getMessage()));
        }
    }

    private Result record(Result r) {
        Counter.builder("portfolio.snapshot.runs")
                .description("장 마감 스냅샷 실행 결과")
                .tag("outcome", r.outcome().name().toLowerCase())
                .register(registry)
                .increment();
        if (r.outcome() == Outcome.SUCCESS) {
            log.info("스냅샷 {} 저장 (대사 불일치 {}건)", r.date(), r.mismatches());
        } else if (r.outcome() == Outcome.FAILURE) {
            log.warn("스냅샷 {} 실패: {}", r.date(), r.reason());
        } else {
            log.info("스냅샷 {} 건너뜀: {}", r.date(), r.reason());
        }
        return r;
    }
}
