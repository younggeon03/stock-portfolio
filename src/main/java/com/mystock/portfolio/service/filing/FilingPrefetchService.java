package com.mystock.portfolio.service.filing;

import com.mystock.portfolio.brokerage.BrokerageClient;
import com.mystock.portfolio.brokerage.BrokerageHolding;
import com.mystock.portfolio.common.LogContext;
import com.mystock.portfolio.domain.CompanyAnalysis;
import com.mystock.portfolio.domain.CompanyAnalysisRepository;
import com.mystock.portfolio.external.filing.FilingService;
import com.mystock.portfolio.external.toss.TossMarketDataService;
import com.mystock.portfolio.external.toss.dto.TossStockInfo;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 공시 재무 야간 배치. 내가 가진 종목과 분석해 둔 종목의 공시 재무를 새벽에 미리 받아 둔다.
 *
 * ★ 왜
 * 분석 버튼을 누르면 사실 모으기에서 공시 재무를 읽는데, 저장본이 하루 지났으면 그 자리에서 공시처
 * (DART 는 보고서마다 여러 번)와 결산일 종가(토스)를 부른다. 종목에 따라 수 초씩 걸리고, 그 사이 공시처가
 * 느리거나 막히면 분석이 묵은 재무로 나간다. 새벽에 받아 두면 낮에는 DB 만 읽는다.
 *
 * ★ 언제 (한국 시간)
 * 매일 06:00. 13F 받기(07:00)보다 앞이라 둘이 같은 스케줄러 스레드를 두고 겹치지 않는다.
 * 한국 공시는 전날 저녁까지, 미국 공시는 한국 시간 낮까지 올라오므로 어느 시각이든 하루 늦을 수 있다.
 * 그 하루는 FilingService 의 "하루 지나면 다시 받기" 가 메운다.
 *
 * ★ 언제 건너뛰나
 * 토스 종목정보(ETF 여부·발행주식수)를 못 받은 종목은 부르지 않는다. 주식수 없이 받으면 주당 지표가 빈
 * 재무가 멀쩡한 저장본을 덮어쓴다. 토스 자체가 실패하면(허용 IP 등) 나머지 종목도 같은 이유로 실패하므로
 * 거기서 멈추고 실패로 남긴다. 저장본은 그대로이고 낮에 읽을 때 예전처럼 다시 받는다.
 *
 * ★ 멱등
 * 종목마다 저장본 한 줄(symbol 이 키)을 덮어쓸 뿐이라 두 번 돌아도 결과는 한 벌이다.
 */
@Service
public class FilingPrefetchService {

    private static final Logger log = LoggerFactory.getLogger(FilingPrefetchService.class);

    public enum Outcome { SUCCESS, FAILURE }

    /**
     * @param counts 결과별 종목 수. 키는 FilingService.Prefetch 이름과 SKIPPED(토스 종목정보가 없어 부르지 않음)라
     *               enum 하나로 못 묶어 문자열로 센다
     * @param reason 실패한 이유
     */
    public record Result(Outcome outcome, int targets, Map<String, Integer> counts, long millis, String reason) {
    }

    static final String SKIPPED = "SKIPPED";

    private final List<BrokerageClient> brokerageClients;
    private final CompanyAnalysisRepository analyses;
    private final TossMarketDataService marketData;
    private final FilingService filingService;
    private final MeterRegistry registry;
    private final String ownerKey;
    private final long pauseMillis;

    public FilingPrefetchService(List<BrokerageClient> brokerageClients, CompanyAnalysisRepository analyses,
                                 TossMarketDataService marketData, FilingService filingService,
                                 MeterRegistry registry,
                                 @Value("${filing.prefetch.owner-key:${snapshot.owner-key:}}") String ownerKey,
                                 @Value("${filing.prefetch.pause-ms:500}") long pauseMillis) {
        this.brokerageClients = brokerageClients;
        this.analyses = analyses;
        this.marketData = marketData;
        this.filingService = filingService;
        this.registry = registry;
        this.ownerKey = ownerKey == null || ownerKey.isBlank() ? null : ownerKey.trim();
        this.pauseMillis = pauseMillis;
    }

    @Scheduled(cron = "${filing.prefetch.cron:0 0 6 * * *}", zone = "Asia/Seoul")
    public void scheduled() {
        LogContext.job("batch-filing-prefetch", () -> run()).run();
    }

    /** 지금 한 번 돈다. 스케줄과 수동 실행이 겹치면 뒤 것이 기다린다 */
    public synchronized Result run() {
        long started = System.currentTimeMillis();
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, String> targets = targets();
        int i = 0;
        for (Map.Entry<String, String> t : targets.entrySet()) {
            if (i++ > 0) {
                pause();
            }
            String symbol = t.getKey();
            String country = t.getValue();
            TossStockInfo info;
            try {
                info = marketData.stockInfo(symbol);
            } catch (Exception e) {
                // 토스가 막히면 남은 종목도 전부 같은 이유로 실패한다. 헛걸음하지 않고 멈춘다
                return record(new Result(Outcome.FAILURE, targets.size(), counts,
                        System.currentTimeMillis() - started, "토스 종목정보 실패(" + symbol + "): " + e.getMessage()));
            }
            String result;
            if (info == null || ("KR".equals(country) && info.sharesOutstanding() == null)) {
                result = SKIPPED;
                log.info("{} 공시 재무 미리 받기 건너뜀: 토스 종목정보(주식수)가 없습니다", symbol);
            } else {
                result = filingService.refresh(symbol, country, info.isFund(), info.sharesOutstanding()).name();
            }
            counts.merge(result, 1, Integer::sum);
            Counter.builder("filing.prefetch.symbols")
                    .description("공시 재무 야간 배치의 종목별 결과")
                    .tag("result", result.toLowerCase(Locale.ROOT))
                    .register(registry)
                    .increment();
        }
        return record(new Result(Outcome.SUCCESS, targets.size(), counts,
                System.currentTimeMillis() - started, null));
    }

    /**
     * 받을 종목과 나라(KR/US). 보유 종목이 먼저, 그다음 분석해 둔 종목. 대소문자만 다른 건 하나로.
     * 증권사 하나가 실패해도 나머지 보유와 분석 종목은 받는다(스냅샷과 달리 일부만 받아도 해가 없다).
     */
    Map<String, String> targets() {
        Map<String, String[]> byKey = new LinkedHashMap<>();
        for (BrokerageClient client : brokerageClients) {
            if (!client.isConfigured()) {
                continue;
            }
            try {
                for (BrokerageHolding h : client.holdings(ownerKey)) {
                    if (h.symbol() != null && !h.symbol().isBlank()) {
                        byKey.putIfAbsent(h.symbol().trim().toUpperCase(Locale.ROOT),
                                new String[]{h.symbol().trim(), h.marketCountry()});
                    }
                }
            } catch (Exception e) {
                log.warn("공시 재무 미리 받기: {} 보유 조회 실패, 그 증권사 종목은 건너뜁니다: {}",
                        client.broker().name(), e.getMessage());
            }
        }
        for (CompanyAnalysis a : analyses.findAll()) {
            String symbol = a.getSymbol();
            if (symbol != null && !symbol.isBlank()) {
                byKey.putIfAbsent(symbol.trim().toUpperCase(Locale.ROOT),
                        new String[]{symbol.trim(), FilingService.guessCountry(symbol.trim())});
            }
        }
        Map<String, String> targets = new LinkedHashMap<>();
        byKey.values().forEach(v -> targets.put(v[0], v[1]));
        return targets;
    }

    /** 공시처·토스 호출 한도를 배려해 종목 사이에 잠깐 쉰다. DART 하루 2만 건에는 한참 못 미친다 */
    private void pause() {
        if (pauseMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(pauseMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Result record(Result r) {
        Counter.builder("filing.prefetch.runs")
                .description("공시 재무 야간 배치 실행 결과")
                .tag("outcome", r.outcome().name().toLowerCase(Locale.ROOT))
                .register(registry)
                .increment();
        if (r.outcome() == Outcome.SUCCESS) {
            log.info("공시 재무 미리 받기 끝: {}종목 {} ({}ms)", r.targets(), r.counts(), r.millis());
        } else {
            log.warn("공시 재무 미리 받기 실패: {} (그때까지 {})", r.reason(), r.counts());
        }
        return r;
    }
}
