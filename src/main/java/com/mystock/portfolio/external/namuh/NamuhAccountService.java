package com.mystock.portfolio.external.namuh;

import com.mystock.portfolio.common.AppException;
import com.mystock.portfolio.external.namuh.dto.NamuhAccountsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 나무증권 계좌 목록 조회 + 어느 계좌를 쓸지 결정.
 *
 * 토스의 TossAccountService 와 같은 역할이다.
 * 다른 점은 계좌를 가리키는 값이 숫자(accountSeq)가 아니라 계좌번호 문자열(act_no)이라는 것뿐이다.
 */
@Service
public class NamuhAccountService {

    private static final Logger log = LoggerFactory.getLogger(NamuhAccountService.class);

    /** 주식 잔고 조회가 가능한 계좌 유형 코드 (실제 호출로 확인한 값) */
    private static final String STOCK_ACCOUNT_TYPE = "01";

    private final NamuhApiClient apiClient;
    private final NamuhApiProperties properties;

    /** 자동 선택한 계좌를 기억해둔다 */
    private volatile String autoSelectedAccountNo;

    public NamuhAccountService(NamuhApiClient apiClient, NamuhApiProperties properties) {
        this.apiClient = apiClient;
        this.properties = properties;
    }

    /** 내 계좌 목록 */
    public List<NamuhAccountsResponse.Account> getAccounts() {
        // 이 API 는 요청 본문이 따로 없다. 빈 JSON({}) 을 보낸다.
        NamuhAccountsResponse response = apiClient.post("/n2/acctinfo", Map.of(), NamuhAccountsResponse.class);

        List<NamuhAccountsResponse.Account> accounts =
                response.accounts() == null ? List.of() : response.accounts();

        if (!accounts.isEmpty() && this.autoSelectedAccountNo == null) {
            this.autoSelectedAccountNo = pickStockAccount(accounts).acctNo();
        }
        return accounts;
    }

    /**
     * 주식 잔고를 조회할 수 있는 계좌를 고른다.
     *
     * ★ 왜 첫 번째 계좌를 그냥 쓰면 안 되는가
     * 계좌 목록에는 주식 계좌 말고 다른 유형도 섞여 나온다.
     * 주식 계좌가 아닌 번호로 잔고를 조회하면 "계좌번호를 잘못 입력하셨습니다"(11165) 오류가 난다.
     *
     * 실제로 확인해보니 acct_type 이 "01" 인 계좌가 주식 잔고 조회에 쓰인다.
     * ("03" 계좌로 조회하면 위 오류가 났다)
     * 그래서 "01" 을 우선 고르고, 없으면 첫 번째 계좌로 넘어간다.
     */
    private NamuhAccountsResponse.Account pickStockAccount(List<NamuhAccountsResponse.Account> accounts) {
        return accounts.stream()
                .filter(account -> STOCK_ACCOUNT_TYPE.equals(account.acctType()))
                .findFirst()
                .orElse(accounts.get(0));
    }

    /**
     * 이번 조회에 쓸 계좌번호를 결정한다.
     * 1순위: .env 의 NAMUH_ACCOUNT_NO
     * 2순위: 계좌 목록의 첫 번째 계좌
     */
    public String resolveAccountNo() {
        String configured = properties.accountNoOrNull();
        if (configured != null) {
            return configured;
        }

        String remembered = this.autoSelectedAccountNo;
        if (remembered != null) {
            return remembered;
        }

        List<NamuhAccountsResponse.Account> accounts = getAccounts();
        if (accounts.isEmpty()) {
            throw new AppException("나무증권 계좌가 조회되지 않았습니다. NAMUH PLUG 에서 API 사용신청 상태를 확인하세요.");
        }

        NamuhAccountsResponse.Account chosen = pickStockAccount(accounts);
        this.autoSelectedAccountNo = chosen.acctNo();
        log.info("나무증권 계좌를 자동 선택했습니다. acctType={}", chosen.acctType());
        return chosen.acctNo();
    }
}
