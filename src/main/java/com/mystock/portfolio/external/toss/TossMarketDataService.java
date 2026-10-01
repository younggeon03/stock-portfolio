package com.mystock.portfolio.external.toss;

import com.mystock.portfolio.external.toss.dto.TossApiEnvelope;
import com.mystock.portfolio.external.toss.dto.TossCandle;
import com.mystock.portfolio.external.toss.dto.TossCandlePage;
import com.mystock.portfolio.external.toss.dto.TossExchangeRate;
import com.mystock.portfolio.external.toss.dto.TossListedStock;
import com.mystock.portfolio.external.toss.dto.TossPrice;
import com.mystock.portfolio.external.toss.dto.TossStockInfo;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 시세(캔들) 조회 담당.
 *
 * 캔들은 계좌와 무관한 공개 시세라서 X-Tossinvest-Account 헤더가 필요 없다. (accountSeq 를 null 로 넘긴다)
 */
@Service
public class TossMarketDataService {

    private static final ParameterizedTypeReference<TossApiEnvelope<TossCandlePage>> CANDLES_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private static final ParameterizedTypeReference<TossApiEnvelope<List<TossPrice>>> PRICES_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private static final ParameterizedTypeReference<TossApiEnvelope<TossExchangeRate>> EXCHANGE_RATE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private static final ParameterizedTypeReference<TossApiEnvelope<List<TossStockInfo>>> STOCK_INFO_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private static final ParameterizedTypeReference<TossApiEnvelope<List<TossListedStock>>> LISTED_STOCKS_TYPE =
            new ParameterizedTypeReference<>() {
            };

    /** 토스가 한 번에 주는 최대 봉 개수 */
    private static final int MAX_COUNT = 200;

    /** 현재가 다건 조회 최대 종목 수 */
    private static final int MAX_SYMBOLS_PER_CALL = 200;

    private final TossApiClient apiClient;

    public TossMarketDataService(TossApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * 일봉을 과거 → 현재 순서로 돌려준다.
     *
     * 토스는 최신 봉을 맨 앞에 주는데(내림차순), 변동성 계산과 차트 그리기는
     * 둘 다 "예전 것부터 순서대로" 가 필요해서 여기서 한 번 뒤집어 준다.
     * 이걸 서비스마다 각자 뒤집으면 실수하기 쉬우므로 입구에서 정리한다.
     */
    public List<TossCandle> dailyCandlesOldestFirst(String symbol, int count) {
        int safeCount = Math.max(2, Math.min(count, MAX_COUNT));

        TossCandlePage page = apiClient.get(
                null, CANDLES_TYPE,
                "/api/v1/candles?symbol={symbol}&interval=1d&count={count}",
                symbol, safeCount);

        if (page.candles() == null || page.candles().isEmpty()) {
            return List.of();
        }

        List<TossCandle> ascending = new ArrayList<>(page.candles());
        java.util.Collections.reverse(ascending);
        return ascending;
    }

    /** 과거 종가를 찾으려고 넘겨볼 최대 페이지. 200봉 × 6 = 약 4년 10개월 */
    private static final int MAX_HISTORY_PAGES = 6;

    /** 결산일이 휴장일이면 그 전 거래일 종가를 쓴다. 이보다 멀면 못 찾은 것으로 본다 */
    private static final int MAX_GAP_DAYS = 10;

    /**
     * 여러 날짜의 종가. 각 날짜 당일, 휴장이면 그 직전 거래일 종가.
     *
     * 과거 PER 을 재려고 만들었다. 결산일 주가 ÷ 그 해 EPS 가 "그 회사가 그때 받던 배수" 다.
     * 토스는 한 번에 200봉까지라 nextBefore 로 페이지를 넘기며 가장 이른 날짜까지 내려간다.
     *
     * @return 찾은 날짜만 담는다. 값은 [실제 거래일, 종가]
     */
    public Map<LocalDate, Map.Entry<LocalDate, BigDecimal>> closesOnOrBefore(String symbol, List<LocalDate> dates) {
        if (dates == null || dates.isEmpty()) {
            return Map.of();
        }
        LocalDate earliest = dates.stream().min(Comparator.naturalOrder()).orElseThrow();

        // 날짜 → 종가. 최신순으로 오므로 모으기만 하고 나중에 찾는다
        java.util.TreeMap<LocalDate, BigDecimal> closes = new java.util.TreeMap<>();
        String before = null;
        for (int pageNo = 0; pageNo < MAX_HISTORY_PAGES; pageNo++) {
            TossCandlePage page = before == null
                    ? apiClient.get(null, CANDLES_TYPE,
                            "/api/v1/candles?symbol={symbol}&interval=1d&count={count}", symbol, MAX_COUNT)
                    : apiClient.get(null, CANDLES_TYPE,
                            "/api/v1/candles?symbol={symbol}&interval=1d&count={count}&before={before}",
                            symbol, MAX_COUNT, before);
            if (page.candles() == null || page.candles().isEmpty()) {
                break;
            }
            for (TossCandle candle : page.candles()) {
                if (candle.timestamp() != null && candle.timestamp().length() >= 10 && candle.closePrice() != null) {
                    closes.put(LocalDate.parse(candle.timestamp().substring(0, 10)), candle.closePrice());
                }
            }
            // 가장 이른 날짜보다 더 과거까지 받았으면 그만 넘긴다
            if (!closes.isEmpty() && closes.firstKey().isBefore(earliest)) {
                break;
            }
            before = page.nextBefore();
            if (before == null || before.isBlank()) {
                break;
            }
        }

        Map<LocalDate, Map.Entry<LocalDate, BigDecimal>> result = new java.util.LinkedHashMap<>();
        for (LocalDate date : dates) {
            Map.Entry<LocalDate, BigDecimal> found = closes.floorEntry(date);
            if (found != null && !found.getKey().isBefore(date.minusDays(MAX_GAP_DAYS))) {
                result.put(date, Map.entry(found.getKey(), found.getValue()));
            }
        }
        return result;
    }

    /**
     * 여러 종목의 현재가를 한 번에 조회한다.
     *
     * ※ 지금 화면에서는 쓰지 않는다. 보유 종목의 현재가는 이미 보유주식 응답에 들어있기 때문이다.
     *   아직 사지 않은 종목의 가격이 필요해질 때를 위해 남겨둔 검증된 코드다.
     *
     * 종목이 없으면 API 를 아예 부르지 않고 빈 목록을 돌려준다.
     */
    public List<TossPrice> prices(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return List.of();
        }
        if (symbols.size() > MAX_SYMBOLS_PER_CALL) {
            throw new IllegalArgumentException(
                    "현재가는 한 번에 " + MAX_SYMBOLS_PER_CALL + "종목까지 조회할 수 있습니다.");
        }

        // 콤마로 이어 붙인다. 예: "NVDA,TSLA,AAPL"
        String joined = String.join(",", symbols);
        return apiClient.get(null, PRICES_TYPE, "/api/v1/prices?symbols={symbols}", joined);
    }

    /**
     * 종목 기본 정보를 조회한다.
     *
     * ★ 기업분석에서 이 API 가 중요한 이유
     * 재무 데이터는 없지만, LLM 이 추측하면 안 되는 값 세 가지를 확실하게 알려준다.
     *   securityType      → ETF 인지 개별 주식인지 (ETF 에 PER/ROE 를 찾는 헛수고를 막는다)
     *   leverageFactor    → 레버리지 배수 (SOXL 은 3.0. 위험 경고의 근거)
     *   sharesOutstanding → 발행주식수 (현재가를 곱하면 시가총액)
     * 국내 종목이면 정리매매·거래정지 여부까지 온다.
     */
    public List<TossStockInfo> stockInfo(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return List.of();
        }
        if (symbols.size() > MAX_SYMBOLS_PER_CALL) {
            throw new IllegalArgumentException(
                    "종목 정보는 한 번에 " + MAX_SYMBOLS_PER_CALL + "종목까지 조회할 수 있습니다.");
        }

        String joined = String.join(",", symbols);
        return apiClient.get(null, STOCK_INFO_TYPE, "/api/v1/stocks?symbols={symbols}", joined);
    }

    /**
     * 마켓 하나의 전체 종목 목록.
     *
     * 종목 마스터를 만드는 용도다. 마켓당 수천 건이 한 번에 오므로 자주 부르면 안 된다.
     * 토스 문서도 "하루 1회 조회 후 로컬 캐싱" 을 권장한다.
     *
     * @param market KOSPI / KOSDAQ / NYSE / NASDAQ / AMEX / KR_ETC / US_ETC
     */
    public List<TossListedStock> listedStocks(String market) {
        return apiClient.get(null, LISTED_STOCKS_TYPE,
                "/api/v1/stocks/all?market={market}&status=ACTIVE", market);
    }

    /** 종목 하나의 기본 정보. 없으면 null */
    public TossStockInfo stockInfo(String symbol) {
        List<TossStockInfo> found = stockInfo(List.of(symbol));
        return found.isEmpty() ? null : found.get(0);
    }

    /**
     * 1달러가 몇 원인지.
     *
     * 포트폴리오 비중 계산과 리밸런싱 계산 양쪽에서 필요해서 여기(시세 담당)로 모아두었다.
     */
    public BigDecimal usdToKrwRate() {
        TossExchangeRate exchangeRate = apiClient.get(
                null, EXCHANGE_RATE_TYPE,
                "/api/v1/exchange-rate?baseCurrency={base}&quoteCurrency={quote}",
                "USD", "KRW");

        BigDecimal rate = exchangeRate.rate();
        if (rate == null || rate.signum() <= 0) {
            throw new IllegalStateException("환율이 0 이하로 내려왔습니다: " + rate);
        }
        return rate;
    }
}
