package com.mystock.portfolio.service.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mystock.portfolio.common.AppException;
import com.mystock.portfolio.domain.CompanyAnalysis;
import com.mystock.portfolio.external.anthropic.AnthropicProperties;
import com.mystock.portfolio.external.anthropic.ClaudeAnalysisClient;
import com.mystock.portfolio.external.anthropic.ClaudeCallResult;
import com.mystock.portfolio.external.filing.FilingService;
import com.mystock.portfolio.external.filing.CompanyFinancials;
import com.mystock.portfolio.external.toss.TossMarketDataService;
import com.mystock.portfolio.external.toss.dto.TossPrice;
import com.mystock.portfolio.external.toss.dto.TossStockInfo;
import com.mystock.portfolio.service.TossAnalysisService;
import com.mystock.portfolio.service.TossAnalysisView;
import com.mystock.portfolio.service.UnifiedPortfolioService;
import com.mystock.portfolio.service.UnifiedPortfolioView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 기업분석 전체를 조율한다.
 *
 * 하는 일
 *   1. 캐시를 먼저 본다 (돈이 나가는 호출을 아끼려고)
 *   2. 없으면 앱이 아는 확정 사실을 모은다
 *   3. 클로드에게 분석을 시킨다 (몇 분 걸리므로 백그라운드)
 *   4. 결과를 저장한다
 *
 * ★ 비중은 항상 전체 합산(ALL) 기준으로 계산한다
 * 화면에서 "토스만" 탭을 보고 있어도 마찬가지다.
 * SOXL 은 토스만 보면 2.98% 지만 합산하면 81% 다. 위험 경고가 완전히 뒤집힌다.
 * 분석은 언제나 진짜 비중을 기준으로 해야 의미가 있다.
 */
@Service
public class CompanyAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(CompanyAnalysisService.class);

    /** 분석에 쓸 일봉 기간 */
    private static final int CANDLE_DAYS = 60;

    /** 비중이 이 퍼센트를 넘으면 집중 위험으로 본다 */
    private static final BigDecimal CONCENTRATION_THRESHOLD = BigDecimal.valueOf(30);

    /** RUNNING 상태가 이 시간을 넘으면 죽은 작업으로 보고 재시작을 허용한다 */
    private static final Duration STALE_RUNNING = Duration.ofMinutes(15);

    /** 섹션이 화면에 나와야 하는 순서 */
    /*
     * 화면에 나오는 순서.
     *
     * ★ 평단가 비교가 맨 앞이다.
     * 이 분석을 여는 이유가 "내가 지금 어떤 상태인가" 라서, 재무제표부터 읽게 하면 안 된다.
     * 결론(판정) → 내 자리(평단가 비교) → 감당 중인 위험 순으로 먼저 보여주고,
     * 회사 자체에 대한 조사는 그 뒤에 둔다. 리스크는 섹션이 아니라 따로 붙는다(화면에서 처리).
     */
    private static final List<String> SECTION_ORDER = List.of(
            "POSITION_REVIEW",
            "FINANCIAL_POSITION", "VALUATION_METRICS", "GUIDANCE_CONSENSUS",
            "REVENUE_BREAKDOWN", "BUSINESS_ANALYSIS", "PROFIT_STRUCTURE");

    /** 섹션 기본 제목 (모델이 제목을 빠뜨렸을 때 대신 쓴다) */
    private static final Map<String, String> SECTION_TITLES = Map.of(
            "FINANCIAL_POSITION", "재무상태",
            "VALUATION_METRICS", "수치 지표",
            "GUIDANCE_CONSENSUS", "가이던스와 컨센서스",
            "REVENUE_BREAKDOWN", "매출구조",
            "BUSINESS_ANALYSIS", "사업분석",
            "PROFIT_STRUCTURE", "매출·수익구조",
            "POSITION_REVIEW", "현재가 vs 내 평단가");

    private final UnifiedPortfolioService portfolioService;
    private final TossMarketDataService marketDataService;
    private final TossAnalysisService analysisService;
    private final ClaudeAnalysisClient claudeClient;
    private final CompanyAnalysisPromptBuilder promptBuilder;
    private final CompanyAnalysisStore store;
    private final AnthropicProperties anthropicProperties;
    private final ObjectMapper objectMapper;
    private final FilingService filingService;

    /**
     * 분석을 돌리는 스레드.
     *
     * 하나짜리로 두는 이유: 동시에 여러 건이 돌면 API 비용이 그만큼 배로 나간다.
     * 데몬 스레드라 앱을 종료할 때 붙잡지 않는다.
     */
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "company-analysis");
        thread.setDaemon(true);
        return thread;
    });

    public CompanyAnalysisService(UnifiedPortfolioService portfolioService,
                                  TossMarketDataService marketDataService,
                                  TossAnalysisService analysisService,
                                  ClaudeAnalysisClient claudeClient,
                                  CompanyAnalysisPromptBuilder promptBuilder,
                                  CompanyAnalysisStore store,
                                  AnthropicProperties anthropicProperties,
                                  ObjectMapper objectMapper,
                                  FilingService filingService) {
        this.portfolioService = portfolioService;
        this.marketDataService = marketDataService;
        this.analysisService = analysisService;
        this.claudeClient = claudeClient;
        this.promptBuilder = promptBuilder;
        this.store = store;
        this.anthropicProperties = anthropicProperties;
        this.objectMapper = objectMapper;
        this.filingService = filingService;
    }

    /** 캐시 조회만 한다. 클로드를 부르지 않으므로 즉시 응답한다. */
    public CompanyAnalysisResponse find(String symbol) {
        return store.find(symbol)
                .map(this::toResponse)
                .orElseGet(() -> CompanyAnalysisResponse.none(symbol));
    }

    /** 여러 종목의 분석 유무만 가볍게. 표의 "기업분석" 칸을 채울 때 쓴다. */
    public List<CompanyAnalysisResponse> findAll(List<String> symbols) {
        Map<String, CompanyAnalysis> found = new LinkedHashMap<>();
        for (CompanyAnalysis entity : store.findAll(symbols)) {
            found.put(entity.getSymbol(), entity);
        }

        List<CompanyAnalysisResponse> result = new ArrayList<>();
        for (String symbol : symbols) {
            CompanyAnalysis entity = found.get(symbol);
            result.add(entity == null ? CompanyAnalysisResponse.none(symbol) : toResponse(entity));
        }
        return result;
    }

    /**
     * 분석을 시작한다. 실제 호출은 백그라운드에서 돌고, 여기서는 바로 RUNNING 을 돌려준다.
     *
     * @param refresh true 면 캐시가 있어도 다시 분석한다
     */
    public CompanyAnalysisResponse start(String symbol, boolean refresh, String ownerKey) {
        Optional<CompanyAnalysis> cached = store.find(symbol);

        // 1. 캐시가 있고 새로고침이 아니면 그대로 돌려준다 (클로드를 부르지 않는다)
        if (!refresh && cached.isPresent() && cached.get().hasAnalysis()
                && CompanyAnalysis.STATUS_OK.equals(cached.get().getStatus())) {
            return toResponse(cached.get());
        }

        // 2. 이미 돌고 있으면 중복으로 부르지 않는다
        if (cached.isPresent() && CompanyAnalysis.STATUS_RUNNING.equals(cached.get().getStatus())) {
            boolean recent = cached.get().getUpdatedAt()
                    .isAfter(LocalDateTime.now().minus(STALE_RUNNING));
            if (recent) {
                return toResponse(cached.get());
            }
            log.warn("{} 이전 분석이 {}분 넘게 끝나지 않아 다시 시작합니다", symbol, STALE_RUNNING.toMinutes());
        }

        // 3. 확정 사실을 모은다 (여기서 실패하면 아예 시작하지 않는다)
        CompanyAnalysisFacts facts = collectFacts(symbol, ownerKey);

        store.beginRun(symbol, facts.name());

        // 4. 백그라운드로 넘긴다
        worker.submit(() -> runAnalysis(facts));

        return store.find(symbol).map(this::toResponse)
                .orElseGet(() -> CompanyAnalysisResponse.none(symbol));
    }

    /** 백그라운드에서 실제로 클로드를 부르는 부분 */
    private void runAnalysis(CompanyAnalysisFacts facts) {
        String symbol = facts.symbol();
        try {
            log.info("{} 기업분석 시작", symbol);
            ClaudeCallResult result = claudeClient.analyze(
                    promptBuilder.systemPrompt(),
                    promptBuilder.userPrompt(facts));

            // 저장하기 전에 파싱이 되는지 확인한다. 깨진 JSON 을 저장해두면 화면이 못 읽는다.
            parseAndNormalize(result.analysisJson(), symbol);

            // 평단가가 들어간 분석인지 같이 남긴다. 공개 화면은 들어가지 않은 것만 보여준다
            store.saveSuccess(symbol, result, facts.held());
            log.info("{} 기업분석 저장 완료", symbol);

        } catch (Exception e) {
            log.warn("{} 기업분석 실패: {}", symbol, e.getMessage());
            store.saveFailure(symbol, e.getMessage());
        }
    }

    // ── 확정 사실 모으기 ─────────────────────────────────

    private CompanyAnalysisFacts collectFacts(String symbol, String ownerKey) {
        // 비중은 반드시 전체 합산 기준으로 구한다
        UnifiedPortfolioView portfolio = portfolioService.load(UnifiedPortfolioService.SCOPE_ALL, ownerKey);

        // 안 가진 종목도 분석한다(기업분석 화면에서 아무 종목이나 연다). 그때는 평단가 없이 현재가만으로
        UnifiedPortfolioView.Item item = portfolio.items().stream()
                .filter(i -> i.symbol().equalsIgnoreCase(symbol))
                .findFirst()
                .orElse(null);
        boolean held = item != null;
        String sym = held ? item.symbol() : symbol;

        // 종목 기본 정보 (ETF 여부·레버리지). 가진 종목은 실패해도 분석은 진행한다.
        TossStockInfo stockInfo = null;
        try {
            stockInfo = marketDataService.stockInfo(sym);
        } catch (Exception e) {
            log.warn("{} 종목 기본정보 조회 실패(계속 진행): {}", symbol, e.getMessage());
        }

        // 안 가진 종목은 이름·시장·현재가를 포트폴리오에서 못 얻는다. 토스에서 받는다
        String name = held ? item.name() : stockInfo == null ? sym : stockInfo.name();
        String country = held ? item.marketCountry() : FilingService.guessCountry(sym);
        String currency = held ? item.currency()
                : stockInfo != null && stockInfo.currency() != null ? stockInfo.currency()
                : "KR".equals(country) ? "KRW" : "USD";
        BigDecimal lastPrice = held ? item.lastPrice() : currentPrice(sym);
        if (!held && lastPrice == null) {
            // 현재가도 없으면 분석할 근거가 없다. 돈을 쓰기 전에 멈춘다
            throw new IllegalArgumentException("현재가를 받지 못해 분석할 수 없습니다: " + symbol
                    + " (티커가 맞는지, 토스 허용 IP 가 등록돼 있는지 확인하세요)");
        }

        // 시세 통계. 실패해도 분석은 진행한다 (토스 호출 한도에 걸려도 막히면 안 된다).
        BigDecimal annualVol = null;
        BigDecimal dailyVol = null;
        BigDecimal periodReturn = null;
        Integer dataPoints = null;
        BigDecimal high = null;
        BigDecimal low = null;
        try {
            List<TossAnalysisView.Volatility> vols = analysisService.volatilities(List.of(sym), CANDLE_DAYS);
            if (!vols.isEmpty() && vols.get(0).error() == null) {
                TossAnalysisView.Volatility v = vols.get(0);
                annualVol = v.annualizedVolatilityPercent();
                dailyVol = v.dailyVolatilityPercent();
                periodReturn = v.periodReturnPercent();
                dataPoints = v.dataPoints();
            }

            TossAnalysisView.Chart chart = analysisService.chart(sym, CANDLE_DAYS);
            if (chart.points() != null && !chart.points().isEmpty()) {
                high = chart.points().stream().map(TossAnalysisView.Chart.Point::close)
                        .max(Comparator.naturalOrder()).orElse(null);
                low = chart.points().stream().map(TossAnalysisView.Chart.Point::close)
                        .min(Comparator.naturalOrder()).orElse(null);
            }
        } catch (Exception e) {
            log.warn("{} 시세 통계 조회 실패(계속 진행): {}", symbol, e.getMessage());
        }

        String brokers = !held ? null : item.lots().stream()
                .map(UnifiedPortfolioView.Lot::brokerName)
                .distinct()
                .reduce((a, b) -> a + " + " + b)
                .orElse("-");

        // 안 가진 종목이면 보유 값은 전부 비운다. 0 을 넣으면 모델이 "0주 보유" 로 읽는다
        return new CompanyAnalysisFacts(
                sym,
                name,
                stockInfo == null ? null : stockInfo.englishName(),
                country,
                stockInfo == null ? country : stockInfo.market(),
                currency,
                stockInfo == null ? "UNKNOWN" : stockInfo.securityType(),
                stockInfo == null ? null : stockInfo.leverageFactor(),
                stockInfo == null ? null : stockInfo.sharesOutstanding(),
                held,
                held ? item.quantity() : null,
                lastPrice,
                held ? item.averagePurchasePrice() : null,
                held ? priceGapPercent(item.lastPrice(), item.averagePurchasePrice()) : null,
                held ? item.marketValueKrw() : null,
                held ? item.purchaseKrw() : null,
                held ? item.profitLossKrw() : null,
                held ? item.profitRatePercent() : null,
                held ? item.weightPercent() : null,
                held ? portfolio.totalValueKrw() : null,
                brokers,
                annualVol, dailyVol, periodReturn, dataPoints, high, low,
                riskFlags(item, stockInfo, annualVol),
                financials(sym, country, stockInfo, lastPrice),
                LocalDateTime.now());
    }

    /** 안 가진 종목의 현재가. 못 받으면 null */
    private BigDecimal currentPrice(String symbol) {
        try {
            List<TossPrice> prices = marketDataService.prices(List.of(symbol));
            return prices.isEmpty() ? null : prices.get(0).lastPrice();
        } catch (Exception e) {
            log.warn("{} 현재가 조회 실패: {}", symbol, e.getMessage());
            return null;
        }
    }

    /**
     * 공시 재무. 한국은 DART, 미국은 SEC EDGAR. ETF 는 재무가 없어 부르지 않는다.
     * 실패해도 null 을 돌려줄 뿐 분석은 진행한다.
     */
    private CompanyFinancials financials(String symbol, String country, TossStockInfo info, BigDecimal price) {
        return filingService.find(symbol, country, info != null && info.isFund(),
                info == null ? null : info.sharesOutstanding(), price).orElse(null);
    }

    /** 평단가 대비 현재가가 몇 퍼센트인지 */
    private BigDecimal priceGapPercent(BigDecimal lastPrice, BigDecimal averagePurchasePrice) {
        if (lastPrice == null || averagePurchasePrice == null || averagePurchasePrice.signum() == 0) {
            return null;
        }
        return lastPrice.subtract(averagePurchasePrice)
                .multiply(BigDecimal.valueOf(100))
                .divide(averagePurchasePrice, 2, RoundingMode.HALF_UP);
    }

    /**
     * 확정 데이터로 판정하는 위험 신호.
     *
     * ★ 이걸 LLM 판단에 맡기지 않는 이유
     * "비중이 81% 다" 는 계산으로 확정되는 사실이다. 모델이 놓치거나 얼버무리면
     * 이 분석의 가장 중요한 경고가 사라진다. 그래서 앱이 먼저 세워서 넘긴다.
     */
    private List<String> riskFlags(UnifiedPortfolioView.Item item, TossStockInfo info, BigDecimal annualVol) {
        List<String> flags = new ArrayList<>();

        // item 이 null 이면 안 가진 종목이다. 비중·손실처럼 내 보유에서 나오는 신호는 건너뛴다
        if (item != null && item.weightPercent() != null
                && item.weightPercent().compareTo(CONCENTRATION_THRESHOLD) > 0) {
            flags.add("이 한 종목이 전체 자산의 " + item.weightPercent().stripTrailingZeros().toPlainString()
                    + "% 를 차지한다. 단일 종목 집중 위험이 크다.");
        }

        if (info != null && info.isLeveraged()) {
            flags.add(info.leverageFactor().stripTrailingZeros().toPlainString()
                    + "배 레버리지 상품이다. 일일 리밸런싱 구조상 횡보장에서도 가치가 깎이는 감쇠 효과가 있다.");
        }

        if (info != null && info.isFund()) {
            flags.add("개별 기업이 아니라 " + info.securityType() + " 상품이다. 기업 재무제표로 분석할 수 없다.");
        }

        if (info != null && info.isLiquidationTrading()) {
            flags.add("정리매매가 진행 중이다. 상장폐지 절차에 들어간 종목이다.");
        }

        if (info != null && info.isTradingSuspended()) {
            flags.add("KRX 거래정지 상태다.");
        }

        if (info != null && info.isDelisting()) {
            flags.add("상장폐지되었거나 예정된 종목이다.");
        }

        if (annualVol != null && annualVol.compareTo(BigDecimal.valueOf(80)) > 0) {
            flags.add("연환산 변동성이 " + annualVol.stripTrailingZeros().toPlainString()
                    + "% 로 매우 높다. 단기간에 큰 폭으로 움직일 수 있다.");
        }

        if (item != null && item.profitRatePercent() != null
                && item.profitRatePercent().compareTo(BigDecimal.valueOf(-20)) < 0) {
            flags.add("평가손실이 " + item.profitRatePercent().stripTrailingZeros().toPlainString()
                    + "% 다. 손실 구간에서의 판단임을 감안해야 한다.");
        }

        return flags;
    }

    // ── 응답 만들기 ──────────────────────────────────────

    private CompanyAnalysisResponse toResponse(CompanyAnalysis entity) {
        CompanyAnalysisView view = null;
        if (entity.hasAnalysis()) {
            try {
                view = parseAndNormalize(entity.getAnalysisJson(), entity.getSymbol());
            } catch (Exception e) {
                log.warn("{} 저장된 분석을 읽지 못했습니다: {}", entity.getSymbol(), e.getMessage());
            }
        }

        Integer ageDays = null;
        boolean stale = false;
        if (entity.getAnalyzedAt() != null) {
            ageDays = (int) Duration.between(entity.getAnalyzedAt(), LocalDateTime.now()).toDays();
            stale = ageDays >= anthropicProperties.cacheDays();
        }

        return new CompanyAnalysisResponse(
                entity.getSymbol(),
                entity.getStatus(),
                entity.getAnalyzedAt(),
                stale,
                ageDays,
                entity.getLastError(),
                CompanyAnalysisResponse.DISCLAIMER,
                entity.getModel(),
                entity.getInputTokens(),
                entity.getOutputTokens(),
                entity.getWebSearchCount(),
                entity.hasAnalysis() ? entity.isIncludesPosition() : null,
                view);
    }

    /**
     * 공개 화면용. 내 평단가가 들어가지 않은 분석(안 가진 종목을 현재가만으로 본 것)만 돌려준다.
     *
     * ★ 들어간 분석이 있어도 "있다" 는 사실조차 알리지 않고 NONE 으로 답한다.
     *   "비공개 분석이 있음" 을 보이면 그 자체로 내가 그 종목을 가졌다는 정보가 샌다.
     * 돌리는 중(RUNNING)·실패 사유도 숨긴다. 같은 이유와, 실패 메시지에 무엇이 들어갈지 모르기 때문이다.
     */
    public CompanyAnalysisResponse findPublic(String symbol) {
        return store.find(symbol)
                .filter(e -> e.hasAnalysis() && !e.isIncludesPosition())
                .map(this::toResponse)
                .filter(r -> r.analysis() != null)
                .map(r -> new CompanyAnalysisResponse(r.symbol(), CompanyAnalysis.STATUS_OK, r.analyzedAt(),
                        r.stale(), r.ageDays(), null, r.disclaimer(), r.model(), null, null,
                        r.webSearchCount(), false, r.analysis()))
                .orElseGet(() -> CompanyAnalysisResponse.none(symbol));
    }

    /**
     * 분석 JSON 을 읽고 모양을 다듬는다.
     *
     * ★ 왜 다듬는가
     * 한 번 호출에 실제 돈이 나간다. 섹션 하나가 빠졌다고 결과를 통째로 버리면 그 돈이 날아간다.
     * 그래서 빠진 섹션은 "확인하지 못함" 으로 채우고, 순서를 맞추고, null 을 빈 값으로 바꿔서
     * 화면이 항상 같은 모양을 그릴 수 있게 만든다.
     * JSON 자체가 깨졌을 때만 예외를 던진다.
     */
    private CompanyAnalysisView parseAndNormalize(String json, String symbol) {
        CompanyAnalysisView raw;
        try {
            raw = objectMapper.readValue(json, CompanyAnalysisView.class);
        } catch (Exception e) {
            throw new AppException("분석 결과 JSON 을 읽지 못했습니다: " + e.getMessage(), e);
        }

        Map<String, CompanyAnalysisView.Section> bySection = new LinkedHashMap<>();
        if (raw.sections() != null) {
            for (CompanyAnalysisView.Section section : raw.sections()) {
                if (section != null && section.key() != null) {
                    bySection.put(section.key(), normalizeSection(section));
                }
            }
        }

        List<CompanyAnalysisView.Section> ordered = new ArrayList<>();
        for (String key : SECTION_ORDER) {
            CompanyAnalysisView.Section section = bySection.get(key);
            ordered.add(section != null ? section : missingSection(key));
        }

        return new CompanyAnalysisView(
                raw.symbol() == null ? symbol : raw.symbol(),
                raw.name() == null ? symbol : raw.name(),
                raw.instrumentType() == null ? "UNKNOWN" : raw.instrumentType(),
                raw.oneLineSummary() == null ? "" : raw.oneLineSummary(),
                normalizeVerdict(raw.verdict()),
                ordered,
                raw.risks() == null ? List.of() : raw.risks());
    }

    /** 판정에 쓸 수 있는 값들. 모델이 엉뚱한 값을 넣으면 UNCLEAR 로 떨어뜨린다 */
    private static final Set<String> STANCES = Set.of("ADD", "HOLD", "TRIM", "EXIT", "UNCLEAR");
    private static final Set<String> BASES =
            Set.of("NEWS", "VALUATION", "MACRO", "INDUSTRY", "PRICE", "CONCENTRATION");

    /** 지표 값의 기간 유형 */
    private static final Set<String> PERIOD_TYPES = Set.of("QUARTER", "ANNUAL", "POINT");

    /**
     * 판정을 다듬는다.
     *
     * ★ 판정은 화면에서 제일 먼저 읽히는 값이라 모르는 값이 그대로 나가면 안 된다.
     * stance 가 비었거나 정의에 없는 값이면 "판단 보류" 로 떨어뜨린다.
     * 방향을 지어내는 것보다 모른다고 하는 쪽이 낫다.
     */
    private CompanyAnalysisView.Verdict normalizeVerdict(CompanyAnalysisView.Verdict verdict) {
        if (verdict == null || verdict.headline() == null || verdict.headline().isBlank()) {
            return new CompanyAnalysisView.Verdict(
                    "UNCLEAR", "판단을 보류합니다",
                    "판정을 내릴 근거가 모이지 않았습니다. 아래 분석 내용을 직접 보세요.", List.of());
        }

        String stance = verdict.stance() == null ? "" : verdict.stance().trim().toUpperCase(Locale.ROOT);
        if (!STANCES.contains(stance)) {
            stance = "UNCLEAR";
        }

        List<String> basis = verdict.basis() == null ? List.<String>of() : verdict.basis().stream()
                .filter(Objects::nonNull)
                .map(b -> b.trim().toUpperCase(Locale.ROOT))
                .filter(BASES::contains)
                .distinct()
                .toList();

        return new CompanyAnalysisView.Verdict(
                stance,
                verdict.headline().trim(),
                verdict.reason() == null ? "" : verdict.reason().trim(),
                basis);
    }

    private CompanyAnalysisView.Section normalizeSection(CompanyAnalysisView.Section section) {
        List<CompanyAnalysisView.Source> sources =
                section.sources() == null ? List.of() : section.sources();

        // 출처 번호가 범위를 벗어나면 "출처 없음"(-1) 으로 낮춘다
        List<CompanyAnalysisView.Metric> metrics = new ArrayList<>();
        if (section.metrics() != null) {
            for (CompanyAnalysisView.Metric metric : section.metrics()) {
                List<CompanyAnalysisView.Metric.Point> points = new ArrayList<>();
                if (metric.points() != null) {
                    for (CompanyAnalysisView.Metric.Point point : metric.points()) {
                        if (point == null) continue;

                        int index = point.sourceIndex();
                        if (index < 0 || index >= sources.size()) {
                            index = -1;
                        }

                        // 정의에 없는 기간 유형이 오면 시점값으로 떨어뜨린다.
                        // 분기/연도 어느 쪽 화면에도 안 걸려 사라지는 것보다 낫다.
                        String type = point.periodType() == null
                                ? "" : point.periodType().trim().toUpperCase(Locale.ROOT);
                        if (!PERIOD_TYPES.contains(type)) {
                            type = "POINT";
                        }

                        points.add(new CompanyAnalysisView.Metric.Point(
                                nvl(point.period()), type, nvl(point.value()), index));
                    }
                }
                if (points.isEmpty()) continue;   // 값이 하나도 없는 지표는 표에서 뺀다

                metrics.add(new CompanyAnalysisView.Metric(
                        nvl(metric.label()), nvl(metric.note()), points));
            }
        }

        String title = section.title() == null || section.title().isBlank()
                ? SECTION_TITLES.getOrDefault(section.key(), section.key())
                : section.title();

        return new CompanyAnalysisView.Section(
                section.key(),
                title,
                section.applicable(),
                nvl(section.notApplicableReason()),
                nvl(section.body()),
                section.bullets() == null ? List.of() : section.bullets(),
                metrics,
                sources);
    }

    /** 모델이 만들지 않은 섹션을 자리만 채워둔다 */
    private CompanyAnalysisView.Section missingSection(String key) {
        return new CompanyAnalysisView.Section(
                key,
                SECTION_TITLES.getOrDefault(key, key),
                false,
                "이 항목은 분석 결과에 포함되지 않았습니다.",
                "",
                List.of(), List.of(), List.of());
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }
}
