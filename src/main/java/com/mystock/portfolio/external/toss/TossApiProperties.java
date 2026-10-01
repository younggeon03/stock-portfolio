package com.mystock.portfolio.external.toss;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 토스증권 Open API 접속 설정.
 *
 * 값이 흘러들어오는 순서:
 *   .env 파일  →  EnvFileLoader 가 System Property 로 등록  →  application.yml 의 ${TOSS_CLIENT_ID} 등이 읽음
 *   →  이 record 에 바인딩
 *
 * record 로 선언하면 스프링이 생성자를 통해 값을 넣어주고(생성자 바인딩), 이후 값이 바뀌지 않는다.
 * PortfolioApplication 에 붙인 @ConfigurationPropertiesScan 덕분에 별도 등록 없이 빈으로 잡힌다.
 */
@ConfigurationProperties(prefix = "toss")
public record TossApiProperties(

        /** API 서버 주소. 항상 https://openapi.tossinvest.com */
        String baseUrl,

        /** 토스증권 WTS > 설정 > Open API 에서 발급받은 클라이언트 ID */
        String clientId,

        /** 클라이언트 시크릿. 절대 코드/깃에 넣지 말고 .env 에만 둔다. */
        String clientSecret,

        /**
         * 사용할 계좌의 accountSeq.
         * 비워두면 계좌 목록을 조회해서 첫 번째 계좌를 자동으로 쓴다. (계좌가 하나뿐이면 비워두면 편하다)
         *
         * Long 이 아니라 String 으로 받는 이유: .env 에 "TOSS_ACCOUNT_SEQ=" 처럼 빈 값으로 두면
         * Long 바인딩이 실패해서 앱이 아예 안 뜬다. 그래서 문자열로 받고 아래에서 직접 변환한다.
         */
        String accountSeq
) {

    /** .env 의 TOSS_ACCOUNT_SEQ 를 숫자로 바꿔준다. 비어 있으면 null (= 자동 선택). */
    public Long accountSeqOrNull() {
        if (accountSeq == null || accountSeq.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(accountSeq.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    ".env 의 TOSS_ACCOUNT_SEQ 는 숫자여야 합니다. 지금 값: " + accountSeq, e);
        }
    }
}
