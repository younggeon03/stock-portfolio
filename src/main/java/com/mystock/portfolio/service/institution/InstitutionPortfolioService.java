package com.mystock.portfolio.service.institution;

import com.mystock.portfolio.domain.CusipTicker;
import com.mystock.portfolio.domain.CusipTickerRepository;
import com.mystock.portfolio.domain.Filing13F;
import com.mystock.portfolio.domain.Filing13FRepository;
import com.mystock.portfolio.domain.Holding13F;
import com.mystock.portfolio.domain.Holding13FRepository;
import com.mystock.portfolio.domain.Institution;
import com.mystock.portfolio.domain.InstitutionRepository;
import com.mystock.portfolio.external.thirteenf.NewFilingEvent;
import com.mystock.portfolio.external.thirteenf.ThirteenFDataChangedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 받아 둔 13F 를 화면용으로 꺼낸다. 바깥을 부르지 않으므로 0원이고 빠르다.
 *
 * 비중은 그 기관의 13F 합계 대비다. 13F 에는 미국 상장 주식·옵션만 들어가서
 * 기관 전체 자산 대비가 아니다. 노르웨이 중앙은행이면 "노르웨이 미국 주식 중 몇 %" 다.
 */
@Service
@Transactional(readOnly = true)
public class InstitutionPortfolioService {

    private final InstitutionRepository institutions;
    private final Filing13FRepository filings;
    private final Holding13FRepository holdings;
    private final CusipTickerRepository tickers;

    public InstitutionPortfolioService(InstitutionRepository institutions, Filing13FRepository filings,
                                       Holding13FRepository holdings, CusipTickerRepository tickers) {
        this.institutions = institutions;
        this.filings = filings;
        this.holdings = holdings;
        this.tickers = tickers;
    }

    /** 기관 목록. 받아 둔 최신 분기 요약을 붙인다 */
    public List<InstitutionView> list() {
        return institutions.findByActiveTrueOrderBySortOrder().stream()
                .map(inst -> {
                    List<Filing13F> fs = visible(inst.getCik());
                    return InstitutionView.of(inst, fs.isEmpty() ? null : fs.get(0),
                            fs.stream().map(Filing13F::getReportPeriod).toList());
                })
                .toList();
    }

    // ── 조회 캐시 ──
    /*
     * ★ 왜 캐시하나 (부하 테스트에서 찾음, 운영 문서 "부하 테스트")
     * 13F 는 하루 한 번(07:00 배치)만 바뀐다. 그런데 "같이 산 종목" 은 요청마다 9곳 × 두 분기 보유(수만 줄)를
     * DB 에서 읽어 비교해 한 번에 1.5~2초가 걸렸고, 동시 사용자 30명에서 p95 가 8초를 넘었다.
     * 그래서 계산 결과를 메모리에 두고, 배치가 데이터를 바꾸면(이벤트) 통째로 비운다. 시간으로 만료시키지 않는다 —
     * 언제 바뀌는지 정확히 알기 때문에 묵은 값을 줄 일이 없다.
     * 크기는 64개로 묶는다(오래 안 쓴 것부터 버림). 아무 period 나 넣는 요청으로 메모리가 차지 않게
     */
    private final QueryCache cache = new QueryCache(64);

    /**
     * 배치가 끝났거나 새 제출을 저장했다 → 비운다.
     * 순서를 맨 앞(0)으로 둔다. 같은 이벤트를 듣는 예열(InstitutionCacheWarmer, 100)이 비운 뒤에 채워야 한다
     */
    @EventListener({ThirteenFDataChangedEvent.class, NewFilingEvent.class})
    @org.springframework.core.annotation.Order(0)
    public void onDataChanged() {
        cache.clear();
    }

    /** 한 기관의 한 분기 보유. period 가 없으면 최신 분기 */
    public Optional<HoldingsView> holdings(long cik, LocalDate period) {
        return holdings(cik, period, 0);
    }

    /** limit 이 0 이하면 전부. 피델리티처럼 5천 줄이 넘는 기관은 화면이 먼저 위 100줄만 받는다 */
    public Optional<HoldingsView> holdings(long cik, LocalDate period, int limit) {
        Optional<HoldingsView> all = cache.get("holdings:" + cik + ":" + period, () -> loadHoldings(cik, period));
        return all.map(v -> v.limited(limit));
    }

    private Optional<HoldingsView> loadHoldings(long cik, LocalDate period) {
        Optional<Institution> inst = institutions.findById(cik);
        if (inst.isEmpty()) {
            return Optional.empty();
        }
        List<Filing13F> fs = visible(cik);
        Optional<Filing13F> filing = period == null ? fs.stream().findFirst()
                : fs.stream().filter(f -> f.getReportPeriod().equals(period)).findFirst();
        if (filing.isEmpty()) {
            return Optional.empty();
        }

        List<Holding13F> rows = holdings.findByAccessionNoOrderByValueUsdDesc(filing.get().getAccessionNo());
        Map<String, CusipTicker> known = tickers.findAllById(rows.stream().map(Holding13F::getCusip).distinct().toList())
                .stream().collect(Collectors.toMap(CusipTicker::getCusip, Function.identity()));
        long total = filing.get().getTotalValueUsd();

        List<HoldingsView.Row> out = rows.stream().map(h -> {
            CusipTicker t = known.get(h.getCusip());
            return new HoldingsView.Row(h.getCusip(), t == null ? null : t.getTicker(), h.getIssuerName(),
                    h.getTitleOfClass(), h.getPutCall().isEmpty() ? null : h.getPutCall(),
                    h.getShares(), h.getValueUsd(), percent(h.getValueUsd(), total));
        }).toList();

        return Optional.of(new HoldingsView(InstitutionView.of(inst.get(), filing.get(),
                fs.stream().map(Filing13F::getReportPeriod).toList()),
                filing.get().getReportPeriod(), filing.get().getFiledDate(), total, out, out.size()));
    }

    /**
     * 한 기관의 분기 변화. period 와 그 바로 앞 분기(보유가 있는)를 비교한다.
     * 앞 분기가 없으면(처음 받은 분기) 비어 있다.
     */
    public Optional<ChangesView> changes(long cik, LocalDate period) {
        return changes(cik, period, 0);
    }

    /** limit 이 0 이하면 전부. counts(종류별 개수)는 limit 과 상관없이 전체 기준이다 */
    public Optional<ChangesView> changes(long cik, LocalDate period, int limit) {
        Optional<ChangesView> all = cache.get("changes:" + cik + ":" + period, () -> loadChanges(cik, period));
        return all.map(v -> v.limited(limit));
    }

    private Optional<ChangesView> loadChanges(long cik, LocalDate period) {
        Optional<Institution> inst = institutions.findById(cik);
        if (inst.isEmpty()) {
            return Optional.empty();
        }
        List<Filing13F> fs = visible(cik);
        int idx = period == null ? 0 : indexOf(fs, period);
        if (idx < 0 || idx + 1 >= fs.size()) {
            return Optional.empty();
        }
        Filing13F now = fs.get(idx);
        Filing13F before = fs.get(idx + 1);
        List<HoldingDiff.Change> all = HoldingDiff.diff(positions(now), positions(before));

        Map<HoldingDiff.Kind, Long> counts = all.stream()
                .collect(Collectors.groupingBy(HoldingDiff.Change::kind, Collectors.counting()));
        List<HoldingDiff.Change> moved = all.stream().filter(c -> c.kind() != HoldingDiff.Kind.UNCHANGED).toList();
        return Optional.of(new ChangesView(
                InstitutionView.of(inst.get(), now, fs.stream().map(Filing13F::getReportPeriod).toList()),
                now.getReportPeriod(), before.getReportPeriod(), counts, moved, moved.size()));
    }

    /**
     * 여러 기관이 같은 분기에 같이 늘리거나 줄인 종목.
     *
     * period 가 없으면, 따라가는 기관의 절반 이상이 보유를 낸 가장 최근 분기를 쓴다.
     * 13F 는 기관마다 내는 날이 달라서(마감 45일) 최신 분기는 몇 곳만 낸 상태일 수 있다.
     * 몇 곳만으로 "여러 기관이 샀다" 고 하면 과장이다.
     *
     * 사실을 세기만 한다. "따라 사라" 는 뜻이 아니다. 13F 는 45일 늦은 자료다.
     */
    public ConsensusView consensus(LocalDate period, int limit) {
        return cache.get("consensus:" + period + ":" + limit, () -> loadConsensus(period, limit));
    }

    private ConsensusView loadConsensus(LocalDate period, int limit) {
        List<Institution> active = institutions.findByActiveTrueOrderBySortOrder();
        Map<Long, List<Filing13F>> byInst = new LinkedHashMap<>();
        active.forEach(i -> byInst.put(i.getCik(), visible(i.getCik())));

        LocalDate target = period != null ? period : defaultConsensusPeriod(byInst.values(), active.size());
        if (target == null) {
            return new ConsensusView(null, 0, List.of(), List.of());
        }

        Map<String, ConsensusRow.Builder> rows = new LinkedHashMap<>();
        int compared = 0;
        for (Institution inst : active) {
            List<Filing13F> fs = byInst.get(inst.getCik());
            int idx = indexOf(fs, target);
            if (idx < 0 || idx + 1 >= fs.size()) {
                continue;
            }
            // 바로 앞 분기와 비교할 수 있어야 같은 석 달의 변화다. 노르웨이 중앙은행은 1·3분기가 비어 있어
            // 2분기를 반년 전과 비교하게 되는데, 그걸 섞으면 "이번 분기에 같이 샀다" 가 틀린다
            if (!fs.get(idx + 1).getReportPeriod().equals(previousQuarterEnd(target))) {
                continue;
            }
            compared++;
            for (HoldingDiff.Change c : HoldingDiff.diff(positions(fs.get(idx)), positions(fs.get(idx + 1)))) {
                ConsensusRow.Builder b = rows.computeIfAbsent(c.cusip(),
                        k -> new ConsensusRow.Builder(c.cusip(), c.ticker(), c.name()));
                switch (c.kind()) {
                    case NEW, ADDED -> b.buyers.add(inst.getNameKo());
                    case REDUCED, SOLD_OUT -> b.sellers.add(inst.getNameKo());
                    default -> { }
                }
            }
        }

        List<ConsensusRow> built = rows.values().stream().map(ConsensusRow.Builder::build).toList();
        List<ConsensusRow> bought = built.stream()
                .filter(r -> r.buyers().size() >= 2)
                .sorted(Comparator.comparingInt((ConsensusRow r) -> r.buyers().size() - r.sellers().size()).reversed()
                        .thenComparing(r -> -r.buyers().size()))
                .limit(limit).toList();
        List<ConsensusRow> sold = built.stream()
                .filter(r -> r.sellers().size() >= 2)
                .sorted(Comparator.comparingInt((ConsensusRow r) -> r.sellers().size() - r.buyers().size()).reversed()
                        .thenComparing(r -> -r.sellers().size()))
                .limit(limit).toList();
        return new ConsensusView(target, compared, bought, sold);
    }

    /**
     * 내 포트폴리오와 기관마다의 겹침. 기관의 최신 분기(보유가 있는)와 비교한다. 겹침이 큰 기관부터.
     * 입력은 저장하지 않는다. 요청 하나 안에서 계산하고 버린다.
     */
    public List<OverlapSummary> overlapAll(List<Overlap.Mine> mine) {
        List<OverlapSummary> out = new java.util.ArrayList<>();
        for (Institution inst : institutions.findByActiveTrueOrderBySortOrder()) {
            List<Filing13F> fs = visible(inst.getCik());
            if (fs.isEmpty()) {
                continue;
            }
            Overlap.Result r = Overlap.compare(mine, theirs(fs.get(0)), 0);
            out.add(new OverlapSummary(inst.getCik(), inst.getNameKo(), inst.getManager(), fs.get(0).getReportPeriod(),
                    r.overlapPercent(), r.shared().size(), r.shared().stream().limit(3).map(Overlap.Shared::ticker).toList()));
        }
        out.sort(Comparator.comparing(OverlapSummary::overlapPercent).reversed());
        return out;
    }

    /** 기관 하나와의 겹침 자세히 */
    public Optional<OverlapDetail> overlapOne(long cik, List<Overlap.Mine> mine) {
        Optional<Institution> inst = institutions.findById(cik);
        if (inst.isEmpty()) {
            return Optional.empty();
        }
        List<Filing13F> fs = visible(cik);
        if (fs.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new OverlapDetail(
                InstitutionView.of(inst.get(), fs.get(0), fs.stream().map(Filing13F::getReportPeriod).toList()),
                Overlap.compare(mine, theirs(fs.get(0)), 10)));
    }

    private List<Overlap.Theirs> theirs(Filing13F filing) {
        return positions(filing).stream()
                .filter(p -> p.ticker() != null)
                .map(p -> new Overlap.Theirs(p.ticker(), p.name(), p.weightPercent()))
                .toList();
    }

    /** 절반 이상이 낸 가장 최근 분기 */
    static LocalDate defaultConsensusPeriod(java.util.Collection<List<Filing13F>> filingsPerInstitution, int total) {
        Map<LocalDate, Integer> count = new java.util.TreeMap<>(Comparator.reverseOrder());
        for (List<Filing13F> fs : filingsPerInstitution) {
            fs.stream().map(Filing13F::getReportPeriod).distinct().forEach(p -> count.merge(p, 1, Integer::sum));
        }
        int need = Math.max(2, (total + 1) / 2);
        return count.entrySet().stream().filter(e -> e.getValue() >= need).map(Map.Entry::getKey)
                .findFirst().orElse(null);
    }

    /**
     * 한 종목을 기관마다 어떻게 들고 있나. 공개 종목 창의 "기관은 어떻게 움직였나" 칸이다.
     *
     * 기관마다 최신 분기를 바로 앞 분기와 비교한다. 바로 앞 분기가 없으면(노르웨이 1·3분기 비공개)
     * 변화는 비우고 보유만 보인다. 반년 전과 비교한 것을 "이번 분기에 늘렸다" 로 보이면 틀린다.
     * 안 가진 기관도 목록에 남긴다. "10곳 중 몇 곳이 갖고 있나" 가 이 칸의 첫 사실이다.
     */
    public StockMovesView movesFor(String ticker) {
        List<String> cusips = tickers.findByTicker(ticker).stream().map(CusipTicker::getCusip).toList();
        List<StockMove> moves = new ArrayList<>();
        LocalDate latest = null;
        for (Institution inst : institutions.findByActiveTrueOrderBySortOrder()) {
            List<Filing13F> fs = visible(inst.getCik());
            if (fs.isEmpty()) {
                continue;
            }
            Filing13F now = fs.get(0);
            if (latest == null || now.getReportPeriod().isAfter(latest)) {
                latest = now.getReportPeriod();
            }
            Filing13F before = fs.size() > 1
                    && fs.get(1).getReportPeriod().equals(previousQuarterEnd(now.getReportPeriod())) ? fs.get(1) : null;

            List<HoldingDiff.Position> nowPos = positionOf(now, cusips, ticker);
            HoldingDiff.Change change = before == null ? null
                    : HoldingDiff.diff(nowPos, positionOf(before, cusips, ticker)).stream().findFirst().orElse(null);
            HoldingDiff.Position held = nowPos.isEmpty() ? null : nowPos.get(0);
            moves.add(new StockMove(inst.getCik(), inst.getNameKo(), now.getReportPeriod(),
                    before == null ? null : before.getReportPeriod(),
                    held == null ? 0 : held.shares(), held == null ? 0 : held.valueUsd(),
                    held == null ? null : held.weightPercent(),
                    change == null ? null : change.kind(),
                    change == null ? null : change.sharesChangePercent(),
                    change == null ? null : change.weightBefore()));
        }
        // 비중이 큰 기관부터. 안 가진 곳은 뒤로
        moves.sort(Comparator.comparing((StockMove m) -> m.weightPercent() == null ? BigDecimal.valueOf(-1) : m.weightPercent())
                .reversed());
        return new StockMovesView(ticker, latest, moves);
    }

    /**
     * 제출 하나에서 이 종목만 꺼내 한 줄로 합친다 (옵션 제외).
     * 티커 하나에 CUSIP 이 여럿이면 합친다. 비교 키(cusip 자리)에 티커를 넣어 두 분기가 같은 줄로 맞물리게 한다.
     */
    private List<HoldingDiff.Position> positionOf(Filing13F filing, List<String> cusips, String ticker) {
        if (cusips.isEmpty()) {
            return List.of();
        }
        List<Holding13F> rows = holdings.findByAccessionNoAndCusipIn(filing.getAccessionNo(), cusips).stream()
                .filter(h -> h.getPutCall().isEmpty()).toList();
        if (rows.isEmpty()) {
            return List.of();
        }
        long shares = rows.stream().mapToLong(Holding13F::getShares).sum();
        long value = rows.stream().mapToLong(Holding13F::getValueUsd).sum();
        return List.of(new HoldingDiff.Position(ticker, ticker, rows.get(0).getIssuerName(), shares, value,
                percent(value, filing.getTotalValueUsd())));
    }

    /** 바로 앞 분기말. 2026-06-30 → 2026-03-31, 2026-03-31 → 2025-12-31 */
    static LocalDate previousQuarterEnd(LocalDate quarterEnd) {
        return quarterEnd.withDayOfMonth(1).minusMonths(2).minusDays(1);
    }

    private static int indexOf(List<Filing13F> fs, LocalDate period) {
        for (int i = 0; i < fs.size(); i++) {
            if (fs.get(i).getReportPeriod().equals(period)) {
                return i;
            }
        }
        return -1;
    }

    /** 분기 하나의 주식 보유 (옵션 제외) */
    private List<HoldingDiff.Position> positions(Filing13F filing) {
        List<Holding13F> rows = holdings.findByAccessionNoOrderByValueUsdDesc(filing.getAccessionNo()).stream()
                .filter(h -> h.getPutCall().isEmpty()).toList();
        Map<String, CusipTicker> known = tickers.findAllById(rows.stream().map(Holding13F::getCusip).distinct().toList())
                .stream().collect(Collectors.toMap(CusipTicker::getCusip, Function.identity()));
        long total = filing.getTotalValueUsd();
        return rows.stream().map(h -> {
            CusipTicker t = known.get(h.getCusip());
            return new HoldingDiff.Position(h.getCusip(), t == null ? null : t.getTicker(), h.getIssuerName(),
                    h.getShares(), h.getValueUsd(), percent(h.getValueUsd(), total));
        }).toList();
    }

    /**
     * 보유가 있는 분기만, 최신부터.
     * 비공개로 낸 빈 제출(노르웨이 중앙은행 1·3분기)을 "최신 분기" 로 고르면 보유가 텅 빈 화면이 된다
     */
    private List<Filing13F> visible(long cik) {
        return filings.findByCikOrderByReportPeriodDesc(cik).stream()
                .filter(f -> f.getHoldingCount() > 0)
                .toList();
    }

    static BigDecimal percent(long part, long whole) {
        if (whole == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(part).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(whole), 2, RoundingMode.HALF_UP);
    }

    /** 기관 한 곳의 요약 */
    public record InstitutionView(long cik, String nameKo, String name, String manager, String note,
                                  LocalDate latestPeriod, LocalDate filedDate, Long totalValueUsd,
                                  Integer holdingCount, List<LocalDate> periods) {

        static InstitutionView of(Institution inst, Filing13F latest, List<LocalDate> periods) {
            return new InstitutionView(inst.getCik(), inst.getNameKo(), inst.getName(), inst.getManager(),
                    inst.getNote(),
                    latest == null ? null : latest.getReportPeriod(),
                    latest == null ? null : latest.getFiledDate(),
                    latest == null ? null : latest.getTotalValueUsd(),
                    latest == null ? null : latest.getHoldingCount(),
                    periods);
        }
    }

    /**
     * 한 기관의 분기 변화. counts 에는 그대로(UNCHANGED)도 세지만 changes 목록에는 바뀐 것만.
     * totalChanges 는 limit 으로 자르기 전 바뀐 종목 수다(화면의 "전체 보기" 버튼)
     */
    public record ChangesView(InstitutionView institution, LocalDate period, LocalDate previousPeriod,
                              Map<HoldingDiff.Kind, Long> counts, List<HoldingDiff.Change> changes, int totalChanges) {

        ChangesView limited(int limit) {
            if (limit <= 0 || limit >= changes.size()) {
                return this;
            }
            return new ChangesView(institution, period, previousPeriod, counts, changes.subList(0, limit), totalChanges);
        }
    }

    /** 기관 하나와의 겹침 요약. topShared 는 겹침이 큰 순서의 티커 셋 */
    public record OverlapSummary(long cik, String nameKo, String manager, LocalDate period,
                                 BigDecimal overlapPercent, int sharedCount, List<String> topShared) {
    }

    public record OverlapDetail(InstitutionView institution, Overlap.Result result) {
    }

    /** 여러 기관을 함께 본 결과. compared 는 이 분기와 앞 분기를 둘 다 낸 기관 수 */
    public record ConsensusView(LocalDate period, int compared, List<ConsensusRow> bought, List<ConsensusRow> sold) {
    }

    /** 한 종목을 늘린 기관과 줄인 기관 */
    public record ConsensusRow(String cusip, String ticker, String name, List<String> buyers, List<String> sellers) {

        static final class Builder {
            private final String cusip;
            private final String ticker;
            private final String name;
            private final List<String> buyers = new java.util.ArrayList<>();
            private final List<String> sellers = new java.util.ArrayList<>();

            Builder(String cusip, String ticker, String name) {
                this.cusip = cusip;
                this.ticker = ticker;
                this.name = name;
            }

            ConsensusRow build() {
                return new ConsensusRow(cusip, ticker, name, List.copyOf(buyers), List.copyOf(sellers));
            }
        }
    }

    /** 한 종목을 따라가는 기관들이 어떻게 들고 있나. period 는 그중 가장 최근 분기 */
    public record StockMovesView(String ticker, LocalDate period, List<StockMove> institutions) {
    }

    /**
     * 기관 한 곳의 이 종목. 안 가졌으면 shares 0, weightPercent null.
     * kind 가 null 이면 바로 앞 분기와 비교할 수 없거나 두 분기 다 안 가진 것
     */
    public record StockMove(long cik, String nameKo, LocalDate period, LocalDate previousPeriod,
                            long shares, long valueUsd, BigDecimal weightPercent,
                            HoldingDiff.Kind kind, BigDecimal sharesChangePercent, BigDecimal weightBefore) {
    }

    /** 한 분기 보유 전체 */
    public record HoldingsView(InstitutionView institution, LocalDate period, LocalDate filedDate,
                               long totalValueUsd, List<Row> holdings, int totalRows) {

        /** 금액 큰 순으로 위 limit 줄만. totalRows 는 그대로 둔다(화면이 "전체 N줄 보기" 를 띄운다) */
        HoldingsView limited(int limit) {
            if (limit <= 0 || limit >= holdings.size()) {
                return this;
            }
            return new HoldingsView(institution, period, filedDate, totalValueUsd, holdings.subList(0, limit), totalRows);
        }

        /** ticker 가 null 이면 아직 못 찾았거나 원래 없는 증권(채권·워런트). putCall 이 null 이면 주식 */
        public record Row(String cusip, String ticker, String issuerName, String titleOfClass, String putCall,
                          long shares, long valueUsd, BigDecimal weightPercent) {
        }
    }
}
