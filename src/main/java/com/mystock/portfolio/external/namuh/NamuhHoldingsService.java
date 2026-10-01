package com.mystock.portfolio.external.namuh;

import com.mystock.portfolio.external.namuh.dto.NamuhGbBalanceResponse;
import com.mystock.portfolio.external.namuh.dto.NamuhKrBalanceResponse;
import org.springframework.stereotype.Service;

/**
 * 나무증권 잔고 조회.
 *
 * ★ 토스와 다른 점: 국내 주식과 해외 주식이 서로 다른 엔드포인트다.
 * 토스는 /api/v1/holdings 하나로 국내·해외를 다 줬는데, 나무는 두 번 불러야 한다.
 */
@Service
public class NamuhHoldingsService {

    /** 국내 주식잔고 */
    private static final String KR_BALANCE_PATH = "/krstock/inquiry/v1/balance";

    /** 해외 주식잔고 */
    private static final String GB_BALANCE_PATH = "/gbstock/inquiry/v1/balance";

    private final NamuhApiClient apiClient;
    private final NamuhAccountService accountService;

    public NamuhHoldingsService(NamuhApiClient apiClient, NamuhAccountService accountService) {
        this.apiClient = apiClient;
        this.accountService = accountService;
    }

    /** 국내 주식 잔고 */
    public NamuhKrBalanceResponse domesticBalance(String accountNo) {
        String account = resolve(accountNo);
        return apiClient.post(KR_BALANCE_PATH,
                NamuhKrBalanceResponse.Request.of(account),
                NamuhKrBalanceResponse.class);
    }

    /** 해외(미국) 주식 잔고 */
    public NamuhGbBalanceResponse overseasBalance(String accountNo) {
        String account = resolve(accountNo);
        return apiClient.post(GB_BALANCE_PATH,
                NamuhGbBalanceResponse.Request.of(account),
                NamuhGbBalanceResponse.class);
    }

    private String resolve(String accountNo) {
        return (accountNo == null || accountNo.isBlank())
                ? accountService.resolveAccountNo()
                : accountNo.trim();
    }
}
