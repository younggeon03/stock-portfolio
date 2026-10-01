package com.mystock.portfolio.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.mystock.portfolio.external.namuh.NamuhAccountService;
import com.mystock.portfolio.external.namuh.NamuhHoldingsService;
import com.mystock.portfolio.external.namuh.dto.NamuhAccountsResponse;
import com.mystock.portfolio.external.namuh.dto.NamuhGbBalanceResponse;
import com.mystock.portfolio.external.namuh.dto.NamuhKrBalanceResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 나무증권 계좌/보유주식 조회 창구.
 *
 * ★★ 주문 API 는 만들지 않는다. 토스와 동일하게 조회·계산만 한다. ★★
 *
 * 확인용 주소:
 *   http://localhost:8080/api/namuh/accounts
 */
@Tag(name = "나무증권 원본", description = "합산 전 나무 응답. 점검용이다")
@RestController
@RequestMapping("/api/namuh")
public class NamuhPortfolioController {

    private final NamuhAccountService accountService;
    private final NamuhHoldingsService holdingsService;

    public NamuhPortfolioController(NamuhAccountService accountService,
                                    NamuhHoldingsService holdingsService) {
        this.accountService = accountService;
        this.holdingsService = holdingsService;
    }

    /** 내 나무증권 계좌 목록. 여기 나온 계좌번호를 .env 의 NAMUH_ACCOUNT_NO 에 적어두면 고정할 수 있다. */
    @GetMapping("/accounts")
    public List<NamuhAccountsResponse.Account> accounts() {
        return accountService.getAccounts();
    }

    /**
     * 국내 주식 잔고 (나무증권 원본 응답 그대로).
     * 필드 매핑이 맞는지 눈으로 확인하는 용도다.
     *
     * @param accountNo 비워두면 .env 값 또는 첫 번째 계좌를 쓴다
     */
    @GetMapping("/holdings/domestic")
    public NamuhKrBalanceResponse domestic(@RequestParam(required = false) String accountNo) {
        return holdingsService.domesticBalance(accountNo);
    }

    /** 해외(미국) 주식 잔고 (나무증권 원본 응답 그대로) */
    @GetMapping("/holdings/overseas")
    public NamuhGbBalanceResponse overseas(@RequestParam(required = false) String accountNo) {
        return holdingsService.overseasBalance(accountNo);
    }
}
