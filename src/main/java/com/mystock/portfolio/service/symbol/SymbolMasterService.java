package com.mystock.portfolio.service.symbol;

import com.mystock.portfolio.domain.SymbolMaster;
import com.mystock.portfolio.domain.SymbolMasterRepository;
import com.mystock.portfolio.external.toss.TossMarketDataService;
import com.mystock.portfolio.external.toss.dto.TossListedStock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 종목명을 종목코드로 바꿔주는 곳.
 *
 * 스크린샷에서 읽은 "현대차" 를 005380 으로 바꾸는 게 이 클래스의 일이다.
 *
 * ★ 왜 정확히 일치하는 것만 찾으면 안 되는가
 * 스크린샷 글자는 완벽하지 않다. 띄어쓰기가 다르거나("현대 차"),
 * 증권사가 이름을 줄여 쓰거나("삼성전자우" vs "삼성전자 우선주"),
 * 글자 하나가 잘못 읽힐 수 있다("헌대차").
 * 그래서 비슷한 것도 찾아주되, 얼마나 확신하는지를 점수로 같이 알려준다.
 */
@Service
public class SymbolMasterService {

    private static final Logger log = LoggerFactory.getLogger(SymbolMasterService.class);

    /** 토스가 제공하는 마켓과 그 마켓의 국가·통화 */
    private static final Map<String, String[]> MARKETS = Map.of(
            "KOSPI", new String[]{"KR", "KRW"},
            "KOSDAQ", new String[]{"KR", "KRW"},
            "KR_ETC", new String[]{"KR", "KRW"},
            "NYSE", new String[]{"US", "USD"},
            "NASDAQ", new String[]{"US", "USD"},
            "AMEX", new String[]{"US", "USD"},
            "US_ETC", new String[]{"US", "USD"});

    /** 이 값보다 덜 닮았으면 아예 후보로 보지 않는다 */
    private static final double MIN_SIMILARITY = 0.55;

    private final SymbolMasterRepository repository;
    private final TossMarketDataService marketDataService;

    /**
     * 검색용 메모리 사본.
     *
     * ★ 왜 메모리에 올리는가
     * 오타("헌대차")를 찾으려면 이름을 하나하나 비교해봐야 한다.
     * DB 로 부분 일치만 걸면 첫 글자가 틀렸을 때 후보조차 못 만든다.
     *
     * 종목이 1만 5천 건인데 이름이 짧아서 전부 비교해도 수십 밀리초면 끝난다.
     * 하루 한 번 갱신되는 고정 데이터라 메모리에 올려두는 게 이득이다.
     */
    private volatile List<SymbolMaster> cache;

    public SymbolMasterService(SymbolMasterRepository repository,
                               TossMarketDataService marketDataService) {
        this.repository = repository;
        this.marketDataService = marketDataService;
    }

    /** 검색용 사본을 가져온다. 처음 부를 때 한 번만 DB 에서 읽는다. */
    private List<SymbolMaster> searchCache() {
        List<SymbolMaster> existing = this.cache;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (this.cache == null) {
                this.cache = repository.findAll();
                log.info("종목 검색 사본을 메모리에 올렸습니다. {}건", this.cache.size());
            }
            return this.cache;
        }
    }

    /** 갱신 후에는 사본을 버려서 다음 검색 때 다시 읽게 한다 */
    private void clearCache() {
        this.cache = null;
    }

    /** 마스터에 종목이 들어있는지 */
    public long size() {
        return repository.count();
    }

    /**
     * 토스에서 전체 종목을 받아와 마스터를 갱신한다.
     *
     * 마켓 7개를 순서대로 부르므로 시간이 좀 걸린다.
     * 한 마켓이 실패해도 나머지는 채운다. (호출 한도에 걸려 전부 실패하면 손해가 크다)
     *
     * @return 마켓별로 몇 건 저장했는지
     */
    @Transactional
    public Map<String, Integer> refreshAll() {
        Map<String, Integer> saved = new java.util.LinkedHashMap<>();
        boolean first = true;

        for (Map.Entry<String, String[]> entry : MARKETS.entrySet()) {
            String market = entry.getKey();
            String country = entry.getValue()[0];
            String currency = entry.getValue()[1];

            // ★ 마켓 사이에 쉬어간다
            // 7개를 연달아 부르면 호출 한도(STOCK_ALL 그룹)에 걸려 뒤쪽 마켓이 통째로 실패한다.
            // 실제로 NASDAQ 와 KOSDAQ 가 빠진 적이 있다. 하루 한 번 도는 작업이라 조금 느려도 괜찮다.
            if (!first) {
                sleepBetweenMarkets();
            }
            first = false;

            try {
                List<TossListedStock> stocks = marketDataService.listedStocks(market);
                int count = 0;
                for (TossListedStock stock : stocks) {
                    if (stock.symbol() == null || stock.name() == null) {
                        continue;
                    }
                    upsert(stock, market, country, currency);
                    count++;
                }
                saved.put(market, count);
                log.info("종목 마스터 갱신: {} {}건", market, count);

            } catch (Exception e) {
                log.warn("종목 마스터 갱신 실패: {} - {}", market, e.getMessage());
                saved.put(market, -1);
            }
        }
        clearCache();
        return saved;
    }

    /** 호출 한도에 걸리지 않도록 마켓 사이에 잠깐 쉰다 */
    private void sleepBetweenMarkets() {
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("종목 마스터 갱신이 중단되었습니다.", e);
        }
    }

    private void upsert(TossListedStock stock, String market, String country, String currency) {
        repository.findBySymbol(stock.symbol())
                .ifPresentOrElse(
                        existing -> existing.refresh(stock.name(), market, country, currency, stock.securityType()),
                        () -> repository.save(new SymbolMaster(
                                stock.symbol(), stock.name(), market, country, currency, stock.securityType())));
    }

    /**
     * 이름(또는 코드)으로 종목을 찾는다.
     *
     * 찾는 순서:
     *   1. 코드가 정확히 일치하는가        → 100점
     *   2. 이름이 정확히 일치하는가        → 100점
     *   3. 한쪽이 다른 쪽에 통째로 들어있는가 → 85점
     *   4. 글자가 얼마나 닮았는가          → 닮은 정도에 비례
     */
    @Transactional(readOnly = true)
    public SymbolMatch findByName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return SymbolMatch.notFound();
        }

        String trimmed = rawName.trim();
        String normalized = SymbolMaster.normalize(trimmed);

        // 1. 종목코드를 그대로 적었을 수도 있다 (005380, NVDA)
        var byCode = repository.findBySymbol(trimmed.toUpperCase());
        if (byCode.isPresent()) {
            return toMatch(byCode.get(), 100);
        }

        // 2. 이름이 정확히 일치
        List<SymbolMaster> exact = repository.findBySearchName(normalized);
        if (!exact.isEmpty()) {
            return toMatch(pickBest(exact), 100);
        }

        // 3~4. 비슷한 것 찾기.
        //     메모리 사본 전체를 훑는다. 첫 글자가 틀린 오타("헌대차")도 이래야 찾힌다.
        //     이름이 짧아서 1만 5천 건을 다 비교해도 수십 밀리초면 끝난다.
        SymbolMaster best = null;
        int bestScore = 0;
        for (SymbolMaster candidate : searchCache()) {
            int score = score(normalized, candidate.getSearchName());
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
                if (bestScore >= 100) {
                    break;   // 더 좋아질 수 없으니 그만 본다
                }
            }
        }

        if (best == null || bestScore == 0) {
            return SymbolMatch.notFound();
        }
        return toMatch(best, bestScore);
    }

    /** 두 이름이 얼마나 닮았는지 0~100 */
    private int score(String query, String target) {
        if (query.equals(target)) {
            return 100;
        }
        // 한쪽이 다른 쪽을 통째로 품고 있으면 꽤 확실하다
        if (target.contains(query) || query.contains(target)) {
            // 길이 차이가 클수록 점수를 깎는다. "삼성" 으로 "삼성전자우선주" 를 찾으면 덜 확실하다
            int longer = Math.max(query.length(), target.length());
            int shorter = Math.min(query.length(), target.length());
            return 70 + (int) (15.0 * shorter / longer);
        }

        double similarity = similarity(query, target);
        if (similarity < MIN_SIMILARITY) {
            return 0;
        }
        // 0.55~1.0 을 30~69 점으로 옮긴다. 확인이 필요한 구간이다.
        return (int) (30 + (similarity - MIN_SIMILARITY) / (1.0 - MIN_SIMILARITY) * 39);
    }

    /** 글자가 얼마나 닮았는지 0.0~1.0 (편집 거리 기반) */
    private double similarity(String a, String b) {
        int distance = levenshtein(a, b);
        int longer = Math.max(a.length(), b.length());
        return longer == 0 ? 1.0 : 1.0 - ((double) distance / longer);
    }

    /**
     * 두 글자열을 같게 만들려면 몇 번 고쳐야 하는지 (편집 거리).
     * "현대차" 와 "헌대차" 는 1번이면 된다.
     */
    private int levenshtein(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];

        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }

        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    /** 이름이 같은 종목이 여럿이면 보통주·국내를 우선한다 */
    private SymbolMaster pickBest(List<SymbolMaster> list) {
        return list.stream()
                .min((a, b) -> {
                    int aScore = "STOCK".equals(a.getSecurityType()) ? 0 : 1;
                    int bScore = "STOCK".equals(b.getSecurityType()) ? 0 : 1;
                    return Integer.compare(aScore, bScore);
                })
                .orElse(list.get(0));
    }

    private SymbolMatch toMatch(SymbolMaster master, int confidence) {
        return new SymbolMatch(master.getSymbol(), master.getName(), master.getMarketCountry(),
                master.getCurrency(), master.getSecurityType(), confidence);
    }
}
