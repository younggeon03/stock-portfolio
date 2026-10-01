package com.mystock.portfolio.external.edgar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mystock.portfolio.common.AppException;
import com.mystock.portfolio.domain.SecCik;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * SEC EDGAR 호출만 담당한다. 해석은 {@link EdgarFinancialService} 가 한다.
 *
 * 둘 다 무료이고 키가 없다. SEC 한도는 초당 10건인데 분석 한 번에 1건이라 걸릴 일이 없다.
 */
@Component
public class EdgarApiClient {

    private static final String TICKERS_URL = "https://www.sec.gov/files/company_tickers.json";
    private static final String FACTS_URL = "https://data.sec.gov/api/xbrl/companyfacts/CIK{cik}.json";

    private final RestClient edgarRestClient;
    private final EdgarProperties properties;
    private final ObjectMapper objectMapper;

    public EdgarApiClient(RestClient edgarRestClient, EdgarProperties properties, ObjectMapper objectMapper) {
        this.edgarRestClient = edgarRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 회사가 XBRL 로 낸 모든 숫자. 태그별로 기간·값·공시번호가 전부 들어 있다.
     * 브로드컴 기준 약 150KB 다.
     */
    public JsonNode companyFacts(long cik) {
        requireUserAgent();
        String body = edgarRestClient.get()
                .uri(FACTS_URL, String.format("%010d", cik))
                .retrieve()
                .body(String.class);
        return read(body, "재무");
    }

    /** 티커 → CIK 매핑 파일. {"0":{"cik_str":1045810,"ticker":"NVDA","title":"NVIDIA CORP"}, ...} */
    public List<SecCik> tickers() {
        requireUserAgent();
        String body = edgarRestClient.get()
                .uri(TICKERS_URL)
                .retrieve()
                .body(String.class);
        return parseTickers(read(body, "티커 매핑"));
    }

    static List<SecCik> parseTickers(JsonNode root) {
        List<SecCik> result = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (JsonNode row : root) {
            String ticker = row.path("ticker").asText("").toUpperCase();
            // 같은 티커가 두 번 나오면 앞의 것이 SEC 가 정한 대표 회사다
            if (ticker.isEmpty() || !seen.add(ticker)) {
                continue;
            }
            result.add(new SecCik(ticker, row.path("cik_str").asLong(), row.path("title").asText(ticker)));
        }
        return result;
    }

    private JsonNode read(String body, String what) {
        // SEC 가 막으면 JSON 이 아니라 HTML 안내문이 온다
        if (body == null || !body.stripLeading().startsWith("{")) {
            throw new AppException("SEC EDGAR " + what + " 응답을 받지 못했습니다. .env 의 SEC_USER_AGENT 에 "
                    + "\"앱이름 이메일\" 형식이 맞는지 확인하세요.");
        }
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new AppException("SEC EDGAR " + what + " JSON 을 읽지 못했습니다: " + e.getMessage(), e);
        }
    }

    private void requireUserAgent() {
        if (!properties.hasUserAgent()) {
            throw new AppException("SEC_USER_AGENT 가 없습니다. .env 에 \"앱이름 이메일\" 형식으로 넣어주세요.");
        }
    }
}
