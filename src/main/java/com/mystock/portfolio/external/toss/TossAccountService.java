package com.mystock.portfolio.external.toss;

import com.mystock.portfolio.common.AppException;
import com.mystock.portfolio.external.toss.dto.TossAccount;
import com.mystock.portfolio.external.toss.dto.TossApiEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 계좌 목록 조회 + "어느 계좌를 쓸지" 결정 담당.
 *
 * 보유주식을 조회하려면 accountSeq 가 필요한데, 이 값을 사용자가 직접 찾아 적게 하면 번거롭다.
 * 그래서 .env 에 TOSS_ACCOUNT_SEQ 가 비어 있으면 계좌 목록을 조회해서 첫 번째 계좌를 자동으로 쓴다.
 */
@Service
public class TossAccountService {

    private static final Logger log = LoggerFactory.getLogger(TossAccountService.class);

    /**
     * 제네릭 타입 정보를 실행 시점까지 유지시키기 위한 장치.
     * 매번 new 하지 않도록 상수로 빼두었다.
     */
    private static final ParameterizedTypeReference<TossApiEnvelope<List<TossAccount>>> ACCOUNTS_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final TossApiClient apiClient;
    private final TossApiProperties properties;

    /** 자동 선택한 계좌를 기억해둔다. 매번 계좌 목록을 다시 부르지 않으려고. */
    private volatile Long autoSelectedAccountSeq;

    public TossAccountService(TossApiClient apiClient, TossApiProperties properties) {
        this.apiClient = apiClient;
        this.properties = properties;
    }

    /**
     * 내 계좌 목록. 계좌 헤더가 필요 없는 API 라 accountSeq 는 null 로 넘긴다.
     *
     * 이 API 는 호출 한도(ACCOUNT 그룹)가 꽤 빡빡해서 짧은 간격으로 두 번 부르면 429 가 난다.
     * 그래서 한 번 불렀을 때 첫 계좌를 기억해두고, 이후 resolveAccountSeq() 가 재사용하게 한다.
     */
    public List<TossAccount> getAccounts() {
        List<TossAccount> accounts = apiClient.get(null, ACCOUNTS_TYPE, "/api/v1/accounts");
        if (!accounts.isEmpty() && this.autoSelectedAccountSeq == null) {
            this.autoSelectedAccountSeq = accounts.get(0).accountSeq();
        }
        return accounts;
    }

    /**
     * 이번 조회에 사용할 계좌 번호를 결정한다.
     * 1순위: .env 의 TOSS_ACCOUNT_SEQ
     * 2순위: 계좌 목록의 첫 번째 계좌 (한 번 고르면 기억해둔다)
     */
    public Long resolveAccountSeq() {
        Long configured = properties.accountSeqOrNull();
        if (configured != null) {
            return configured;
        }

        Long remembered = this.autoSelectedAccountSeq;
        if (remembered != null) {
            return remembered;
        }

        List<TossAccount> accounts = getAccounts();
        if (accounts.isEmpty()) {
            throw new AppException("""
                    토스증권 계좌가 조회되지 않았습니다.
                    종합매매(BROKERAGE) 계좌가 있는지 확인하세요. 계좌가 없으면 보유주식을 조회할 수 없습니다.""");
        }

        TossAccount first = accounts.get(0);
        this.autoSelectedAccountSeq = first.accountSeq();
        log.info("사용할 계좌를 자동 선택했습니다. accountSeq={}, accountType={}", first.accountSeq(), first.accountType());
        return first.accountSeq();
    }
}
