package com.mystock.portfolio.external.toss;

import com.mystock.portfolio.common.AppException;
import com.mystock.portfolio.external.toss.dto.TossApiEnvelope;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * 토큰이 필요한 토스 API 를 호출하는 공용 창구.
 *
 * 이 클래스가 맡는 반복 작업 3가지:
 *   1. Authorization: Bearer {토큰} 헤더 붙이기
 *   2. 계좌 관련 API 라면 X-Tossinvest-Account 헤더 붙이기
 *   3. {"result": ...} 껍데기를 벗겨서 알맹이만 돌려주기
 *
 * 덕분에 서비스 코드는 "무슨 데이터를 원한다" 만 적으면 된다.
 */
@Component
public class TossApiClient {

    /** 계좌 지정이 필요한 API 에 붙이는 헤더 이름 */
    private static final String ACCOUNT_HEADER = "X-Tossinvest-Account";

    private final RestClient tossRestClient;
    private final TossAuthService authService;
    private final PublicIpLookup publicIpLookup;

    public TossApiClient(RestClient tossRestClient, TossAuthService authService, PublicIpLookup publicIpLookup) {
        this.tossRestClient = tossRestClient;
        this.authService = authService;
        this.publicIpLookup = publicIpLookup;
    }

    /**
     * 토스 API 를 GET 으로 부른다.
     *
     * @param accountSeq  계좌 헤더가 필요 없으면 null (시세/환율 등)
     * @param responseType 받을 타입. new ParameterizedTypeReference&lt;TossApiEnvelope&lt;내타입&gt;&gt;() {} 형태로 넘긴다.
     *                     제네릭은 실행 시점에 타입 정보가 지워지는데, 이 방식으로 넘기면 Jackson 이 타입을 알 수 있다.
     * @param uriTemplate "/api/v1/prices?symbols={symbols}" 처럼 중괄호로 자리를 비워둔 주소
     * @param uriVars     중괄호에 채워 넣을 값들. 특수문자는 스프링이 알아서 URL 인코딩해준다.
     */
    public <T> T get(Long accountSeq,
                     ParameterizedTypeReference<TossApiEnvelope<T>> responseType,
                     String uriTemplate,
                     Object... uriVars) {
        try {
            return callOnce(accountSeq, responseType, uriTemplate, uriVars);

        } catch (HttpClientErrorException.Unauthorized e) {
            // 401 = 토큰이 죽었다는 뜻.
            // (다른 곳에서 토큰을 새로 발급받으면 우리가 들고 있던 토큰이 무효화된다)
            // 들고 있던 토큰을 버리고 새로 받아서 딱 한 번만 다시 시도한다.
            authService.invalidate();
            try {
                return callOnce(accountSeq, responseType, uriTemplate, uriVars);
            } catch (RestClientResponseException retryFailure) {
                throw new AppException(describeError(retryFailure, uriTemplate), retryFailure);
            }

        } catch (RestClientResponseException e) {
            throw new AppException(describeError(e, uriTemplate), e);

        } catch (AppException e) {
            // 아래 callOnce 에서 던진 우리 예외는 그대로 통과시킨다
            throw e;

        } catch (Exception e) {
            throw new AppException("토스증권 호출 실패(" + uriTemplate + "): " + e.getMessage(), e);
        }
    }

    /** 실제 HTTP 한 번 쏘는 부분 */
    private <T> T callOnce(Long accountSeq,
                           ParameterizedTypeReference<TossApiEnvelope<T>> responseType,
                           String uriTemplate,
                           Object... uriVars) {
        TossApiEnvelope<T> envelope = tossRestClient.get()
                .uri(uriTemplate, uriVars)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + authService.getAccessToken())
                .headers(headers -> {
                    if (accountSeq != null) {
                        headers.set(ACCOUNT_HEADER, String.valueOf(accountSeq));
                    }
                })
                .retrieve()
                .body(responseType);

        if (envelope == null || envelope.result() == null) {
            throw new AppException("토스증권이 빈 응답을 돌려줬습니다: " + uriTemplate);
        }
        return envelope.result();
    }

    /** HTTP 상태코드를 사람이 읽을 수 있는 한글 안내로 바꾼다. */
    private String describeError(RestClientResponseException e, String uriTemplate) {
        int status = e.getStatusCode().value();
        String head = "토스증권 호출 실패 (" + uriTemplate + ", HTTP " + status + "). ";
        return head + switch (status) {
            case 400 -> "요청 형식이 잘못되었습니다. 계좌 헤더(X-Tossinvest-Account)가 빠졌는지 확인하세요.";
            case 401 -> "인증에 실패했습니다. .env 의 키를 확인하고 앱을 재시작하세요.";
            case 403 -> "허용되지 않은 IP 입니다.\n"
                    + "토스증권 WTS > 설정 > Open API > 허용 IP 관리 에서 등록하세요.\n"
                    + publicIpLookup.registerGuide();
            case 404 -> "대상을 찾을 수 없습니다. 종목코드나 계좌 정보를 확인하세요.";
            case 429 -> "요청 한도를 초과했습니다. 잠시 기다렸다가 다시 시도하세요.";
            default -> "응답 본문: " + e.getResponseBodyAsString();
        };
    }
}
