package com.mystock.portfolio.external.dart;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 단일회사 전체 재무제표(fnlttSinglAcntAll) 응답의 한 줄.
 *
 * 요약(fnlttSinglAcnt) 에 없는 지배주주 몫을 꺼내려고만 쓴다. 한 보고서에 300줄 가까이 오지만
 * 필요한 건 둘뿐이라 IFRS 계정 ID 로 고른다. 한글 계정명은 회사마다 "지배기업소유주지분", "지배기업의 소유주" 처럼 다르다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DartOwnerRow(
        /** BS 재무상태표 / IS 손익계산서 / CIS 포괄손익계산서 */
        @JsonProperty("sj_div") String sjDiv,
        @JsonProperty("account_id") String accountId,
        @JsonProperty("thstrm_amount") String thisTermAmount,
        @JsonProperty("thstrm_add_amount") String thisTermCumulative,
        @JsonProperty("frmtrm_amount") String priorTermAmount,
        @JsonProperty("frmtrm_add_amount") String priorTermCumulative,
        @JsonProperty("bfefrmtrm_amount") String twoTermsAgoAmount
) {

    /** 지배기업 소유주에게 귀속되는 당기순이익 */
    public static final String OWNERS_PROFIT = "ifrs-full_ProfitLossAttributableToOwnersOfParent";

    /** 지배기업 소유주지분 (자본) */
    public static final String OWNERS_EQUITY = "ifrs-full_EquityAttributableToOwnersOfParent";

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(String status, String message, List<DartOwnerRow> list) {
    }
}
