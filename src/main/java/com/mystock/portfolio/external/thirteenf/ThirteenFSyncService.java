package com.mystock.portfolio.external.thirteenf;

import com.mystock.portfolio.domain.CusipTicker;
import com.mystock.portfolio.domain.CusipTickerRepository;
import com.mystock.portfolio.domain.Filing13F;
import com.mystock.portfolio.domain.Filing13FRepository;
import com.mystock.portfolio.domain.Holding13F;
import com.mystock.portfolio.domain.Holding13FRepository;
import com.mystock.portfolio.domain.Institution;
import com.mystock.portfolio.domain.InstitutionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 13F 를 매일 받아 DB 에 쌓는 배치.
 *
 * ★ 두 단계로 나눈다
 * 1. 제출 받기: 기관마다 새 13F 가 있으면 보유 표를 받아 저장한다. 이미 받은 접수번호는 건너뛴다.
 *    13F 는 분기에 한 번이라 대부분의 날은 기관당 SEC 호출 1번(제출 목록)으로 끝난다.
 * 2. 티커 찾기: 새로 나온 CUSIP 만 OpenFIGI 에 묻는다. 한도 때문에 느려서 뒤에 따로 돈다.
 *    티커가 아직 없어도 화면은 회사 이름으로 보여줄 수 있으니 1단계를 기다리게 하지 않는다.
 *
 * ★ 같은 배치가 겹치지 않게 한다
 * 아침 스케줄과 수동 실행(POST)이 겹치면 같은 접수를 두 번 넣다가 유니크 키에 걸린다.
 * 돌고 있으면 새로 시작하지 않는다.
 *
 * ★ 기관 하나가 실패해도 나머지는 받는다
 * 13F 표는 대행사마다 모양이 조금씩 달라서 한 곳이 깨질 수 있다. 그 기관만 기록하고 넘어간다.
 */
@Service
public class ThirteenFSyncService {

    private static final Logger log = LoggerFactory.getLogger(ThirteenFSyncService.class);

    /** SEC 는 초당 10번까지다. 넉넉히 쉰다 */
    private static final long SEC_PAUSE_MILLIS = 150;

    private final ThirteenFApiClient secClient;
    private final OpenFigiClient figiClient;
    private final InstitutionRepository institutions;
    private final Filing13FRepository filings;
    private final Holding13FRepository holdings;
    private final CusipTickerRepository tickers;
    private final TransactionTemplate tx;
    private final org.springframework.context.ApplicationEventPublisher events;
    private final int quarters;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<Status> lastStatus = new AtomicReference<>(Status.never());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "13f-sync");
        t.setDaemon(true);
        return t;
    });

    public ThirteenFSyncService(ThirteenFApiClient secClient, OpenFigiClient figiClient,
                                InstitutionRepository institutions, Filing13FRepository filings,
                                Holding13FRepository holdings, CusipTickerRepository tickers,
                                TransactionTemplate tx, org.springframework.context.ApplicationEventPublisher events,
                                @Value("${thirteenf.quarters:8}") int quarters) {
        this.secClient = secClient;
        this.figiClient = figiClient;
        this.institutions = institutions;
        this.filings = filings;
        this.holdings = holdings;
        this.tickers = tickers;
        this.tx = tx;
        this.events = events;
        this.quarters = quarters;
    }

    /** 마지막 실행 결과. 화면과 운영 확인용 */
    public record Status(boolean running, LocalDateTime startedAt, LocalDateTime finishedAt,
                         int newFilings, int newHoldings, int resolvedCusips, int unresolvedLeft,
                         List<String> errors) {
        static Status never() {
            return new Status(false, null, null, 0, 0, 0, 0, List.of());
        }
    }

    public Status status() {
        Status s = lastStatus.get();
        return new Status(running.get(), s.startedAt(), s.finishedAt(), s.newFilings(), s.newHoldings(),
                s.resolvedCusips(), s.unresolvedLeft(), s.errors());
    }

    /** 매일 아침 (한국 시간). 미국 장 마감 뒤 SEC 가 그날 접수를 공개한 다음이다 */
    @Scheduled(cron = "${thirteenf.sync-cron:0 0 7 * * *}", zone = "Asia/Seoul")
    public void scheduled() {
        if (!secClient.isConfigured()) {
            return;   // SEC 연락처가 없으면 조용히 건너뛴다. 공시 재무와 같은 규칙
        }
        runNow();
    }

    /** 뒤에서 돌린다. 이미 돌고 있으면 false */
    public boolean startAsync() {
        if (running.get()) {
            return false;
        }
        worker.submit(this::runNow);
        return true;
    }

    /** 1단계와 2단계를 차례로 */
    void runNow() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        LocalDateTime started = LocalDateTime.now();
        lastStatus.set(new Status(true, started, null, 0, 0, 0, 0, List.of()));
        List<String> errors = new java.util.ArrayList<>();
        int[] counts = new int[2];
        int[] resolved = new int[1];
        try {
            for (Institution inst : institutions.findByActiveTrueOrderBySortOrder()) {
                try {
                    syncInstitution(inst, counts);
                } catch (Exception e) {
                    log.warn("13F {} ({}) 받기 실패: {}", inst.getNameKo(), inst.getCik(), e.getMessage());
                    errors.add(inst.getNameKo() + ": " + e.getMessage());
                }
            }
            resolved[0] = resolveTickers();
        } catch (Exception e) {
            log.warn("13F 배치 실패: {}", e.getMessage());
            errors.add(e.getMessage());
        } finally {
            int left = holdings.findUnresolvedCusips().size();
            lastStatus.set(new Status(false, started, LocalDateTime.now(), counts[0], counts[1], resolved[0], left,
                    List.copyOf(errors)));
            running.set(false);
            log.info("13F 배치 끝: 새 제출 {}건, 보유 {}줄, 티커 {}개 찾음, 남은 CUSIP {}개, 실패 {}건",
                    counts[0], counts[1], resolved[0], left, errors.size());
        }
    }

    private void syncInstitution(Institution inst, int[] counts) throws InterruptedException {
        List<ThirteenFApiClient.FilingRef> refs = secClient.recentFilings(inst.getCik(), quarters);
        Thread.sleep(SEC_PAUSE_MILLIS);

        for (ThirteenFApiClient.FilingRef ref : refs) {
            if (filings.existsById(ref.accessionNo())) {
                continue;
            }
            List<InfoTableParser.Row> raw = secClient.holdings(inst.getCik(), ref.accessionNo());
            Thread.sleep(SEC_PAUSE_MILLIS * 2);   // 파일 목록 + 표, 두 번 불렀다
            List<InfoTableParser.Row> rows = InfoTableParser.normalizeUnits(raw);
            if (rows != raw) {
                log.info("13F {} {} 분기는 금액이 천 달러 단위라 달러로 바꿨습니다", inst.getNameKo(), ref.reportPeriod());
            }
            save(inst.getCik(), ref, rows);
            // 저장(커밋)이 끝난 뒤에 알린다. 알림 쪽이 실패해도 받은 자료는 남는다
            events.publishEvent(new NewFilingEvent(inst.getCik(), inst.getNameKo(), ref.reportPeriod(), ref.filedDate(), rows.size()));
            counts[0]++;
            counts[1] += rows.size();
            log.info("13F {} {} 분기 {}줄 저장", inst.getNameKo(), ref.reportPeriod(), rows.size());
        }
        dropOldQuarters(inst.getCik());
    }

    /**
     * 보유 줄을 먼저 넣고 제출 행을 마지막에 넣는다. 한 트랜잭션이다.
     * 중간에 죽으면 제출 행이 없으니 다음 실행이 같은 접수를 처음부터 다시 받는다.
     * 보유가 0줄인 비공개 제출도 제출 행은 남긴다. 안 남기면 매일 다시 받는다.
     */
    private void save(long cik, ThirteenFApiClient.FilingRef ref, List<InfoTableParser.Row> rows) {
        long total = rows.stream().mapToLong(InfoTableParser.Row::valueUsd).sum();
        tx.executeWithoutResult(status -> {
            holdings.deleteByAccessionNo(ref.accessionNo());
            holdings.saveAll(rows.stream().map(r -> new Holding13F(ref.accessionNo(), r.cusip(),
                    truncate(r.issuerName(), 200), truncate(r.titleOfClass(), 100), r.putCall(),
                    r.shareType(), r.shares(), r.valueUsd())).toList());
            filings.save(new Filing13F(ref.accessionNo(), cik, ref.reportPeriod(), ref.filedDate(), total, rows.size()));
        });
    }

    /** 최근 N 분기만 남긴다. 화면이 쓰는 건 "이번 분기와 지난 분기" 정도라 무한히 쌓을 이유가 없다 */
    private void dropOldQuarters(long cik) {
        List<Filing13F> all = filings.findByCikOrderByReportPeriodDesc(cik);
        for (Filing13F old : all.stream().skip(quarters).toList()) {
            tx.executeWithoutResult(status -> {
                holdings.deleteByAccessionNo(old.getAccessionNo());
                filings.delete(old);
            });
        }
    }

    /** 아직 모르는 CUSIP 을 OpenFIGI 에 묻는다. 찾은 개수를 돌려준다 */
    int resolveTickers() throws InterruptedException {
        List<String> pending = holdings.findUnresolvedCusips();
        int found = 0;
        int batch = figiClient.batchSize();
        int limitedInARow = 0;
        for (int i = 0; i < pending.size(); i += batch) {
            List<String> chunk = pending.subList(i, Math.min(i + batch, pending.size()));
            Map<String, OpenFigiClient.Figi> result;
            try {
                result = figiClient.map(chunk);
                limitedInARow = 0;
            } catch (OpenFigiClient.RateLimited e) {
                // 쉬어도 계속 막히면 오늘은 접는다. 남은 건 내일 배치가 이어서 한다(못 찾은 것만 다시 묻는다)
                if (++limitedInARow >= 5) {
                    log.warn("OpenFIGI 한도가 계속 막혀 오늘은 여기까지 ({} / {})", i, pending.size());
                    break;
                }
                log.info("OpenFIGI 한도에 걸려 1분 쉽니다 ({} / {})", i, pending.size());
                Thread.sleep(60_000);
                i -= batch;   // 같은 묶음을 다시
                continue;
            }
            List<CusipTicker> rows = result.entrySet().stream()
                    .map(e -> new CusipTicker(e.getKey(), stockTicker(e.getValue().ticker()),
                            truncate(e.getValue().name(), 200), truncate(e.getValue().securityType(), 50)))
                    .toList();
            tickers.saveAll(rows);
            found += (int) rows.stream().filter(r -> r.getTicker() != null).count();
            Thread.sleep(figiClient.pauseMillis());
        }
        return found;
    }

    /** cusip_ticker.ticker 칸 길이. V2 에서 정했다 */
    static final int TICKER_MAX = 20;

    /**
     * OpenFIGI 가 돌려준 "티커" 중 주식 티커로 쓸 것만.
     * 채권·워런트는 "T 4.5 05/15/38" 처럼 긴 이름이 티커 자리에 온다. 그대로 넣으면 칸 길이를 넘어
     * 저장이 통째로 실패한다(2026-10-03 기관 교체 때 이 한 줄 때문에 4,300개 변환이 멈췄다).
     * 잘라서 넣으면 엉뚱한 티커가 되므로 "티커 없음" 으로 둔다. 회사 이름(figiName)은 남는다.
     */
    static String stockTicker(String ticker) {
        return ticker == null || ticker.length() > TICKER_MAX || ticker.contains(" ") ? null : ticker;
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
