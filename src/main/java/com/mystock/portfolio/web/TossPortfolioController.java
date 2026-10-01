package com.mystock.portfolio.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.mystock.portfolio.external.toss.TossAccountService;
import com.mystock.portfolio.external.toss.TossMarketDataService;
import com.mystock.portfolio.external.toss.dto.TossAccount;
import com.mystock.portfolio.external.toss.dto.TossStockInfo;
import com.mystock.portfolio.service.TossPortfolioService;
import com.mystock.portfolio.service.TossPortfolioView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * 토스 계좌/보유주식을 브라우저에 JSON 으로 내려주는 창구.
 *
 * @RestController 를 붙였으므로 리턴한 자바 객체가 자동으로 JSON 글자로 바뀌어 나간다.
 * 프론트(app.js)는 이 주소를 fetch 로 부르기만 하면 된다.
 *
 * 확인용 주소:
 *   http://localhost:8080/api/toss/accounts   → 내 계좌 목록
 *   http://localhost:8080/api/toss/portfolio  → 보유 종목 + 비중
 */
@Tag(name = "토스증권 원본", description = "합산 전 토스 응답. 점검용이다")
@RestController
@RequestMapping("/api/toss")
public class TossPortfolioController {

    private final TossAccountService accountService;
    private final TossPortfolioService portfolioService;
    private final TossMarketDataService marketDataService;

    public TossPortfolioController(TossAccountService accountService,
                                   TossPortfolioService portfolioService,
                                   TossMarketDataService marketDataService) {
        this.accountService = accountService;
        this.portfolioService = portfolioService;
        this.marketDataService = marketDataService;
    }

    /** 내 계좌 목록. 여기 나온 accountSeq 를 .env 의 TOSS_ACCOUNT_SEQ 에 적어두면 계좌를 고정할 수 있다. */
    @GetMapping("/accounts")
    public List<TossAccount> accounts() {
        return accountService.getAccounts();
    }

    /** 보유 종목 + 평가금액 + 비중. 화면의 표가 이 데이터를 그대로 그린다. */
    @GetMapping("/portfolio")
    public TossPortfolioView portfolio() {
        return portfolioService.load();
    }

    /**
     * 종목 기본 정보. 쉼표로 구분해 여러 종목을 한 번에 조회한다.
     *
     * 기업분석이 이 값을 쓴다. ETF 여부와 레버리지 배수를 여기서 확정하기 때문에
     * LLM 에게 "이게 ETF 맞나요?" 를 묻지 않아도 된다.
     *
     * 예: /api/toss/stocks?symbols=SOXL,005380
     */
    @GetMapping("/stocks")
    public List<TossStockInfo> stocks(@RequestParam String symbols) {
        List<String> parsed = Arrays.stream(symbols.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();

        if (parsed.isEmpty()) {
            throw new IllegalArgumentException("symbols 파라미터가 필요합니다. 예: symbols=SOXL,005380");
        }
        return marketDataService.stockInfo(parsed);
    }
}
