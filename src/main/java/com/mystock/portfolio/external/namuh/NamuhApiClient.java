package com.mystock.portfolio.external.namuh;

import com.mystock.portfolio.common.AppException;
import com.mystock.portfolio.external.namuh.dto.NamuhResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * 토큰이 필요한 나무증권 API 를 호출하는 공용 창구.
 *
 * ★ 토스와 다른 점 세 가지
 *   1. 조회도 전부 POST 다 (토스는 GET + 쿼리 파라미터)
 *   2. 계좌 지정을 헤더가 아니라 요청 본문의 act_no 로 한다
 *   3. HTTP 200 이어도 본문의 rsp_cd 가 "00000" 이 아니면 실패다
 *
 * 3번 때문에 상태코드 검사만으로는 부족하고, 본문까지 확인해야 한다.
 */
@Component
public class NamuhApiClient {

    private final RestClient namuhRestClient;
    private final NamuhAuthService authService;

    public NamuhApiClient(RestClient namuhRestClient, NamuhAuthService authService) {
        this.namuhRestClient = namuhRestClient;
        this.authService = authService;
    }

    /**
     * 나무증권 API 를 POST 로 부른다.
     *
     * @param path         "/n2/acctinfo" 같은 경로
     * @param requestBody  요청 본문 객체. 본문이 없으면 빈 객체를 넘긴다
     * @param responseType 받을 타입
     */
    public <T extends NamuhResponse> T post(String path, Object requestBody, Class<T> responseType) {
        try {
            return callOnce(path, requestBody, responseType);

        } catch (HttpClientErrorException.Unauthorized e) {
            // 토큰이 죽었다는 뜻. 새로 받아서 딱 한 번만 다시 시도한다.
            authService.invalidate();
            try {
                return callOnce(path, requestBody, responseType);
            } catch (RestClientResponseException retryFailure) {
                throw new AppException(describeError(retryFailure, path), retryFailure);
            }

        } catch (RestClientResponseException e) {
            throw new AppException(describeError(e, path), e);

        } catch (AppException e) {
            throw e;

        } catch (Exception e) {
            throw new AppException("나무증권 호출 실패(" + path + "): " + e.getMessage(), e);
        }
    }

    private <T extends NamuhResponse> T callOnce(String path, Object requestBody, Class<T> responseType) {
        T response = namuhRestClient.post()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + authService.getAccessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(responseType);

        if (response == null) {
            throw new AppException("나무증권이 빈 응답을 돌려줬습니다: " + path);
        }

        // HTTP 200 이어도 본문에서 실패라고 할 수 있다
        if (!response.isSuccess()) {
            throw new AppException("나무증권 조회 실패 (" + path + "). "
                    + "코드=" + response.rspCd() + ", 메시지=" + response.rspMsg());
        }
        return response;
    }

    private String describeError(RestClientResponseException e, String path) {
        int status = e.getStatusCode().value();
        String head = "나무증권 호출 실패 (" + path + ", HTTP " + status + "). ";
        return head + switch (status) {
            case 400 -> "요청 형식이 잘못되었습니다. 요청 본문 항목을 확인하세요.";
            case 401 -> "인증에 실패했습니다. .env 의 키를 확인하고 앱을 재시작하세요.";
            case 403 -> "접근이 거부되었습니다. 해당 API 사용신청이 되어 있는지 확인하세요.";
            case 404 -> "경로를 찾을 수 없습니다: " + path;
            case 429 -> "요청 한도를 초과했습니다. 잠시 기다렸다가 다시 시도하세요.";
            default -> "응답 본문: " + e.getResponseBodyAsString();
        };
    }
}
