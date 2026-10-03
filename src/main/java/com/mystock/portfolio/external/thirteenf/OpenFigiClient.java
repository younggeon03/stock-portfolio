package com.mystock.portfolio.external.thirteenf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CUSIP → 티커. OpenFIGI(블룸버그가 무료로 연 증권 식별자 서비스)에 묻는다.
 *
 * ★ 한도
 * 키 없이: 한 번에 10개, 분에 25번. 키가 있으면: 한 번에 100개, 6초에 25번.
 * 키는 무료(https://www.openfigi.com/api)이고 OPENFIGI_API_KEY 에 넣는다. 없어도 느릴 뿐 동작한다.
 * 노르웨이 중앙은행처럼 수천 종목이면 키 없이 30분쯤 걸리지만 처음 한 번뿐이다(결과를 DB 에 남긴다).
 */
@Component
public class OpenFigiClient {

    private static final String URL = "https://api.openfigi.com/v3/mapping";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final boolean hasKey;

    public OpenFigiClient(ObjectMapper objectMapper, @Value("${openfigi.api-key:}") String apiKey,
                          RestClient.Builder builder) {
        this.objectMapper = objectMapper;
        this.hasKey = apiKey != null && !apiKey.isBlank();
        // 주입받은 Builder 라야 호출 지표가 남는다(HttpClientObservationConfig)
        builder.defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        if (hasKey) {
            builder.defaultHeader("X-OPENFIGI-APIKEY", apiKey.strip());
        }
        this.restClient = builder.build();
    }

    /** 한 번에 물을 수 있는 개수 */
    public int batchSize() {
        return hasKey ? 100 : 10;
    }

    /** 다음 요청까지 쉴 시간(ms). 한도보다 조금 여유 있게 잡는다 */
    public long pauseMillis() {
        return hasKey ? 300 : 2600;
    }

    /** 찾은 결과. 못 찾았으면 ticker 가 null */
    public record Figi(String ticker, String name, String securityType) {
        static final Figi NOT_FOUND = new Figi(null, null, null);
    }

    /** 한도에 걸렸다. 부르는 쪽이 잠시 쉬었다가 이어서 하면 된다 */
    public static class RateLimited extends RuntimeException {
        public RateLimited() {
            super("OpenFIGI 호출 한도에 걸렸습니다");
        }
    }

    /** 보낸 순서대로 결과를 돌려준다. 응답이 요청과 같은 순서로 온다 */
    public Map<String, Figi> map(List<String> cusips) {
        List<Map<String, String>> jobs = new ArrayList<>();
        cusips.forEach(c -> jobs.add(Map.of("idType", "ID_CUSIP", "idValue", c)));
        try {
            String body = restClient.post().uri(URL).body(jobs).retrieve().body(String.class);
            return parse(cusips, objectMapper.readTree(body));
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatusCode.valueOf(429))) {
                throw new RateLimited();
            }
            throw new IllegalStateException("OpenFIGI 응답 오류: " + e.getStatusCode(), e);
        } catch (RateLimited e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("OpenFIGI 응답을 읽지 못했습니다: " + e.getMessage(), e);
        }
    }

    /** 미국 개별 거래소 코드. NYSE(UN)·나스닥(UW·UQ·UR)·NYSE American(UA)·Arca(UP)·OTC(UV) 등 */
    static final Set<String> US_EXCHANGES = Set.of("UN", "UW", "UQ", "UR", "UA", "UP", "UV", "UF", "UD", "UT", "UX", "UB", "UM", "UC");

    /**
     * 거래소가 없는 통합 코드(X1)의 티커 모양. "뿌리 + (숫자) + USD". 예: CCL1USD → CCL, ACCDUSD → ACCD.
     * 상장 구조가 바뀌었거나 인수로 상장폐지된 종목의 옛 CUSIP 을 물으면 미국 거래소 결과 없이 이것만 온다
     */
    private static final Pattern X1_USD = Pattern.compile("([A-Z][A-Z.]*?)\\d*USD");

    /**
     * 한 CUSIP 에 거래소별로 여러 결과가 온다. 고르는 순서:
     * 1. 미국 통합 시세(exchCode=US)
     * 2. 미국 개별 거래소
     * 3. 통합 코드(X1)의 달러 표기에서 뿌리만 (CCL1USD → CCL)
     * 4. 그래도 없으면 티커 없음. 이름만 남긴다
     *
     * ★ 예전에는 4 대신 "첫 번째 결과" 를 썼다. 그러면 독일 거래소 코드(AVU0)나 CCL1USD 같은 값이
     *   티커로 화면에 나왔다(2026-10-03, 카니발이 CCL1USD 로 나와서 발견). 틀린 티커보다 "티커 없음" 이 낫다.
     * 티커의 "/" 는 점으로 바꾼다(BRK/B → BRK.B). 화면에서 흔히 쓰는 표기다.
     */
    static Map<String, Figi> parse(List<String> cusips, JsonNode response) {
        Map<String, Figi> out = new LinkedHashMap<>();
        for (int i = 0; i < cusips.size(); i++) {
            JsonNode data = response.path(i).path("data");
            if (data.size() == 0) {
                out.put(cusips.get(i), Figi.NOT_FOUND);
                continue;
            }
            JsonNode pick = first(data, d -> "US".equals(d.path("exchCode").asText()));
            if (pick == null) {
                pick = first(data, d -> US_EXCHANGES.contains(d.path("exchCode").asText()));
            }
            String ticker = pick == null ? null : pick.path("ticker").asText();
            if (pick == null) {
                for (JsonNode d : data) {
                    Matcher m = X1_USD.matcher(d.path("ticker").asText());
                    if ("X1".equals(d.path("exchCode").asText()) && m.matches()) {
                        pick = d;
                        ticker = m.group(1);
                        break;
                    }
                }
            }
            JsonNode named = pick != null ? pick : data.get(0);
            out.put(cusips.get(i), new Figi(
                    ticker == null ? null : blankToNull(ticker.replace('/', '.')),
                    blankToNull(named.path("name").asText()),
                    blankToNull(named.path("securityType").asText())));
        }
        return out;
    }

    private static JsonNode first(JsonNode data, java.util.function.Predicate<JsonNode> test) {
        for (JsonNode d : data) {
            if (test.test(d)) {
                return d;
            }
        }
        return null;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
