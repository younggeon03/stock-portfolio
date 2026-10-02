package com.mystock.portfolio.service.stock;

import com.mystock.portfolio.external.filing.CompanyFinancials;
import com.mystock.portfolio.external.filing.FilingService;
import com.mystock.portfolio.external.toss.TossMarketDataService;
import com.mystock.portfolio.external.toss.dto.TossPrice;
import com.mystock.portfolio.external.toss.dto.TossStockInfo;
import com.mystock.portfolio.service.institution.InstitutionPortfolioService;
import com.mystock.portfolio.service.institution.InstitutionPortfolioService.StockMovesView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 공개 종목 창: 공시 재무 + 앱이 만든 "읽을 점" + 기관 10곳의 움직임. 전부 0원이다.
 *
 * ★ 왜 6시간 캐시인가
 * 이 창은 로그인 없이 누구나 연다. 열 때마다 SEC·토스를 부르면 방문자 수만큼 바깥 호출이 늘고,
 * SEC 는 초당 10건을 넘기면 IP 를 막는다. 재무는 분기에 한 번 바뀌고 13F 도 하루 한 번 받으므로
 * 6시간 묵은 값이어도 틀리지 않는다. 주가 변화(%)만 그만큼 늦을 수 있다.
 */
@Service
public class StockBriefService {

    private static final Logger log = LoggerFactory.getLogger(StockBriefService.class);

    static final Duration TTL = Duration.ofHours(6);
    /** 캐시가 끝없이 커지지 않게. 넘으면 통째로 비운다(종목 수가 적어 LRU 까지는 필요 없다) */
    static final int MAX_ENTRIES = 500;
    /** 13F 에 나오는 미국 티커만. BRK.B·BF-B 같은 점·하이픈 포함 */
    static final Pattern TICKER = Pattern.compile("[A-Z][A-Z0-9.-]{0,9}");

    private final FilingService filingService;
    private final TossMarketDataService marketData;
    private final InstitutionPortfolioService institutionService;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public StockBriefService(FilingService filingService, TossMarketDataService marketData,
                             InstitutionPortfolioService institutionService) {
        this.filingService = filingService;
        this.marketData = marketData;
        this.institutionService = institutionService;
    }

    public static boolean validTicker(String ticker) {
        return ticker != null && TICKER.matcher(ticker).matches();
    }

    public StockBriefView brief(String ticker) {
        Cached hit = cache.get(ticker);
        if (hit != null && hit.at().plus(TTL).isAfter(Instant.now())) {
            return hit.view();
        }
        StockBriefView view = build(ticker);
        if (cache.size() >= MAX_ENTRIES) {
            cache.clear();
        }
        cache.put(ticker, new Cached(Instant.now(), view));
        return view;
    }

    private StockBriefView build(String ticker) {
        StockMovesView moves = institutionService.movesFor(ticker);

        // 토스는 막혀 있을 수 있다(허용 IP). 그래도 재무와 기관은 나간다. PER·주가 변화만 빈다
        BigDecimal shares = null;
        BigDecimal price = null;
        boolean fund = false;
        try {
            TossStockInfo info = marketData.stockInfo(ticker);
            if (info != null) {
                shares = info.sharesOutstanding();
                fund = info.isFund();
            }
            List<TossPrice> prices = marketData.prices(List.of(ticker));
            price = prices.isEmpty() ? null : prices.get(0).lastPrice();
        } catch (Exception e) {
            log.warn("{} 토스 조회 실패, 재무만 보여줍니다: {}", ticker, e.getMessage());
        }

        CompanyFinancials f = null;
        try {
            f = filingService.find(ticker, "US", fund, shares, price).orElse(null);
        } catch (Exception e) {
            // 공시를 못 읽어도 기관 칸은 보여준다
            log.warn("{} 공시 재무 실패: {}", ticker, e.getMessage());
        }

        BigDecimal priceChange = priceChangeSinceFiscalEnd(f, price);
        return new StockBriefView(ticker, f == null ? null : f.corpName(),
                f == null ? null : StockNotes.summary(f),
                f == null ? List.of() : StockNotes.of(f, priceChange),
                f, priceChange, moves, Instant.now());
    }

    /** 마지막 결산일 종가 대비 지금 주가(%). 둘 중 하나라도 모르면 null */
    static BigDecimal priceChangeSinceFiscalEnd(CompanyFinancials f, BigDecimal price) {
        if (f == null || price == null || f.history() == null || f.history().isEmpty()) {
            return null;
        }
        BigDecimal close = f.history().get(f.history().size() - 1).close();
        if (close == null || close.signum() <= 0) {
            return null;
        }
        return price.subtract(close).multiply(BigDecimal.valueOf(100)).divide(close, 1, RoundingMode.HALF_UP);
    }

    private record Cached(Instant at, StockBriefView view) {
    }

    /**
     * 공개 종목 창 한 장.
     * financials 가 null 이면 공시 재무를 못 찾은 것(ETF 등). 그래도 기관 칸은 채워진다.
     */
    public record StockBriefView(String ticker, String name, String summary, List<StockNotes.Note> notes,
                                 CompanyFinancials financials, BigDecimal priceChangePercent,
                                 StockMovesView institutions, Instant builtAt) {
    }
}
