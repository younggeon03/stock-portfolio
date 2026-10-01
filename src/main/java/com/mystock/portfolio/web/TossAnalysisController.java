package com.mystock.portfolio.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.mystock.portfolio.service.TossAnalysisService;
import com.mystock.portfolio.service.TossAnalysisView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * 변동성/차트 데이터를 내려주는 창구.
 *
 * ★ 왜 포트폴리오 조회와 분리했나
 * 변동성은 종목 수만큼 시세 API 를 불러야 해서 느리고 호출 한도도 잡아먹는다.
 * 표는 빨리 떠야 하니까, 화면에서는 먼저 표를 그리고 변동성은 나중에 따로 불러와서 채워 넣는다.
 *
 * 확인용 주소:
 *   /api/toss/volatility?symbols=NVDA,TSLA
 *   /api/toss/chart?symbol=NVDA
 */
@Tag(name = "시세와 차트", description = "일봉 캔들·이동평균선·변동성. 계좌와 무관해서 어떤 종목이든 조회된다")
@RestController
@RequestMapping("/api/toss")
public class TossAnalysisController {

    /** 한 번에 분석할 수 있는 종목 수 상한. 호출 한도를 지키기 위한 안전장치 */
    private static final int MAX_SYMBOLS = 20;

    private final TossAnalysisService analysisService;

    public TossAnalysisController(TossAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    /**
     * 종목별 변동성.
     *
     * @param symbols 쉼표로 구분한 종목코드. 예: NVDA,TSLA,AAPL
     *                화면이 이미 보유종목 목록을 갖고 있으므로 그대로 넘겨준다.
     *                (서버가 보유종목을 다시 조회하면 API 호출만 늘어난다)
     * @param days    분석 기간(거래일). 기본 60일
     */
    @GetMapping("/volatility")
    public List<TossAnalysisView.Volatility> volatility(
            @RequestParam String symbols,
            @RequestParam(defaultValue = "60") int days) {

        List<String> symbolList = parseSymbols(symbols);
        return analysisService.volatilities(symbolList, days);
    }

    /**
     * 종목 하나의 캔들차트 데이터.
     *
     * 기본 200봉을 주는 이유는 120일 이동평균선 때문이다.
     * 120일선은 121번째 날부터 그려지므로, 60봉만 받으면 선이 아예 안 나온다.
     * 토스가 한 번에 주는 최대치가 정확히 200이라 그만큼 받는다.
     */
    @GetMapping("/chart")
    public TossAnalysisView.Chart chart(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "200") int days) {

        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol 파라미터가 필요합니다.");
        }
        return analysisService.chart(symbol.trim(), days);
    }

    /** "NVDA, TSLA ,AAPL" → ["NVDA", "TSLA", "AAPL"] */
    private List<String> parseSymbols(String symbols) {
        if (symbols == null || symbols.isBlank()) {
            throw new IllegalArgumentException("symbols 파라미터가 필요합니다. 예: symbols=NVDA,TSLA");
        }

        List<String> parsed = Arrays.stream(symbols.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();

        if (parsed.isEmpty()) {
            throw new IllegalArgumentException("분석할 종목이 없습니다.");
        }
        if (parsed.size() > MAX_SYMBOLS) {
            throw new IllegalArgumentException(
                    "한 번에 분석할 수 있는 종목은 " + MAX_SYMBOLS + "개까지입니다. 요청한 종목 수: " + parsed.size());
        }
        return parsed;
    }
}
