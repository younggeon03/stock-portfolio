package com.mystock.portfolio.external.namuh.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * POST /n2/acctinfo (계좌목록조회) 응답.
 *
 * {
 *   "Output_0": [ { "acct_no": "계좌번호", "acct_type": "01" }, ... ],
 *   "rsp_cd": "00000",
 *   "rsp_msg": "조회가 완료되었습니다."
 * }
 *
 * ★ 필드 이름이 Output_0 처럼 대문자로 시작한다.
 * 자바 필드명 규칙(소문자 시작)과 안 맞아서 @JsonProperty 로 연결해준다.
 */
public record NamuhAccountsResponse(

        @JsonProperty("Output_0") List<Account> accounts,

        @JsonProperty("rsp_cd") String rspCd,

        @JsonProperty("rsp_msg") String rspMsg

) implements NamuhResponse {

    @Override
    public boolean hasPayload() {
        return accounts != null && !accounts.isEmpty();
    }

    /** 계좌 한 건 */
    public record Account(

            /** 계좌번호. 잔고 조회 시 요청 본문의 act_no 에 넣는다 */
            @JsonProperty("acct_no") String acctNo,

            /** 계좌 종류 코드. "01" 이 일반 위탁계좌로 보인다 */
            @JsonProperty("acct_type") String acctType
    ) {
    }
}
