package com.mystock.portfolio.external.filing;

import com.mystock.portfolio.external.dart.DartFinancialService;
import com.mystock.portfolio.external.edgar.EdgarFinancialService;
import com.mystock.portfolio.external.toss.TossMarketDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 종목이 어느 나라 것인지 보고 공시처를 고른다. 한국은 DART, 미국은 SEC EDGAR.
 *
 * 부르는 쪽(기업분석, 미리보기 주소)이 공시처를 몰라도 되게 여기서 한 번만 가른다.
 * ETF 는 어느 쪽에도 기업 재무가 없으니 부르지 않는다.
 *
 * 과거 PER·PBR 도 여기서 붙인다. 공시처는 재무만 알고 주가는 토스가 알아서, 둘을 합치는 자리가 여기다.
 */
@Service
public class FilingService {

    private static final Logger log = LoggerFactory.getLogger(FilingService.class);

    private final DartFinancialService dart;
    private final EdgarFinancialService edgar;
    private final TossMarketDataService marketData;

    public FilingService(DartFinancialService dart, EdgarFinancialService edgar, TossMarketDataService marketData) {
        this.dart = dart;
        this.edgar = edgar;
        this.marketData = marketData;
    }

    /**
     * @param marketCountry KR 또는 US
     * @param shares        지금 발행주식수. 없으면 주당 지표가 비거나(DART) SEC 표지 값을 쓴다(EDGAR)
     * @param price         현재가 (거래 통화). 없으면 PER·PBR 이 빈다
     * @return 공시처에 없거나, 키가 없거나, 실패하면 비어 있다. 예외를 던지지 않는다
     */
    public Optional<CompanyFinancials> find(String symbol, String marketCountry, boolean fund,
                                            BigDecimal shares, BigDecimal price) {
        if (fund) {
            return Optional.empty();
        }
        Optional<CompanyFinancials> found = Optional.empty();
        if ("KR".equals(marketCountry)) {
            found = dart.find(symbol, shares, price);
        } else if ("US".equals(marketCountry)) {
            found = edgar.find(symbol, shares, price);
        }
        return found.map(f -> withHistory(symbol, f));
    }

    /**
     * 결산일 종가로 그 해의 PER·PBR 을 잰다.
     *
     * ★ 왜 앱이 재는가
     * 적정가 계산은 "그 회사가 과거에 받아온 PER 범위" 에 기댄다. 이 범위를 웹에서 찾으면 출처마다
     * 기준(연결/별도, 지배/연결 순이익, 결산일/연평균 주가)이 달라 같은 해 PER 이 두세 배씩 벌어진다.
     * 이익은 공시, 주가는 토스에서 같은 기준으로 가져오면 그 문제가 없다.
     *
     * 실패해도 재무는 그대로 돌려준다. 과거 배수가 없으면 예전처럼 클로드가 웹에서 찾는다.
     */
    private CompanyFinancials withHistory(String symbol, CompanyFinancials financials) {
        List<CompanyFinancials.Period> dated = financials.annual().stream()
                .filter(p -> p.periodEnd() != null)
                .toList();
        if (dated.isEmpty()) {
            return financials;
        }
        try {
            Map<LocalDate, Map.Entry<LocalDate, BigDecimal>> closes = marketData.closesOnOrBefore(
                    symbol, dated.stream().map(CompanyFinancials.Period::periodEnd).toList());
            List<CompanyFinancials.Valuation> history = new ArrayList<>();
            for (CompanyFinancials.Period period : dated) {
                Map.Entry<LocalDate, BigDecimal> close = closes.get(period.periodEnd());
                if (close != null) {
                    history.add(CompanyFinancials.Valuation.of(period, close.getKey(), close.getValue()));
                }
            }
            return financials.withHistory(history);
        } catch (Exception e) {
            log.warn("{} 과거 종가 조회 실패, 과거 PER 없이 진행합니다: {}", symbol, e.getMessage());
            return financials;
        }
    }

    /** 국내 6자리 숫자 코드면 KR, 아니면 US 로 본다. 미리보기 주소에서만 쓴다 */
    public static String guessCountry(String symbol) {
        return symbol != null && symbol.matches("\\d{6}") ? "KR" : "US";
    }
}
