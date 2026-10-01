package com.mystock.portfolio.external.thirteenf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mystock.portfolio.common.AppException;
import com.mystock.portfolio.external.edgar.EdgarProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * SEC EDGAR 에서 13F 를 받는다. 공시 재무와 같은 RestClient(연락처 User-Agent)를 쓴다.
 *
 * 순서: 기관의 제출 목록(submissions) → 13F 접수번호 → 그 접수의 파일 목록(index.json) → 보유 표 XML.
 * 보유 표 파일 이름이 제출마다 다르다(56757.xml, infotable.xml, form13fInfoTable.xml ...).
 * 그래서 파일 목록에서 표지(primary_doc.xml)가 아닌 XML 을 큰 것부터 열어 본다.
 */
@Component
public class ThirteenFApiClient {

    private static final String SUBMISSIONS_URL = "https://data.sec.gov/submissions/CIK{cik}.json";
    private static final String INDEX_URL = "https://www.sec.gov/Archives/edgar/data/{cik}/{acc}/index.json";
    private static final String FILE_URL = "https://www.sec.gov/Archives/edgar/data/{cik}/{acc}/{file}";

    /**
     * 13F 금액 단위가 천 달러에서 달러로 바뀐 날.
     * 이 날 전에 낸 13F 는 받지 않는다. 섞이면 같은 종목이 분기마다 천 배씩 오르내린다.
     */
    static final LocalDate DOLLAR_UNIT_SINCE = LocalDate.of(2023, 1, 3);

    private final RestClient edgarRestClient;
    private final EdgarProperties properties;
    private final ObjectMapper objectMapper;

    public ThirteenFApiClient(RestClient edgarRestClient, EdgarProperties properties, ObjectMapper objectMapper) {
        this.edgarRestClient = edgarRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public boolean isConfigured() {
        return properties.hasUserAgent();
    }

    /** 접수 한 건을 가리키는 값 */
    public record FilingRef(String accessionNo, LocalDate reportPeriod, LocalDate filedDate) {
    }

    /** 기관의 최근 13F-HR 원본을 최신 분기부터 */
    public List<FilingRef> recentFilings(long cik, int quarters) {
        return selectFilings(json(SUBMISSIONS_URL, "13F 제출 목록", String.format("%010d", cik)), quarters);
    }

    /** 접수 한 건의 보유 표 */
    public List<InfoTableParser.Row> holdings(long cik, String accessionNo) {
        String acc = accessionNo.replace("-", "");
        JsonNode index = json(INDEX_URL, "13F 파일 목록", cik, acc);
        for (String file : infoTableCandidates(index)) {
            byte[] xml = edgarRestClient.get().uri(FILE_URL, cik, acc, file).retrieve().body(byte[].class);
            if (xml == null || !looksLikeInfoTable(xml)) {
                continue;
            }
            return InfoTableParser.parse(new ByteArrayInputStream(xml));
        }
        throw new AppException("13F 보유 표 파일을 찾지 못했습니다: " + accessionNo);
    }

    /**
     * 제출 목록에서 13F-HR 원본만 고른다.
     *
     * 정정본(13F-HR/A)은 뺀다. 정정은 "전체 다시 내기" 와 "빠진 것만 추가" 두 종류가 있고
     * 목록만 봐서는 어느 쪽인지 모른다. 잘못 합치면 보유가 두 배가 된다. 원본만 써도 분기 그림은 맞다.
     * 같은 분기 원본이 둘이면(드묾) 나중 것을 쓴다.
     */
    static List<FilingRef> selectFilings(JsonNode submissions, int quarters) {
        JsonNode recent = submissions.path("filings").path("recent");
        JsonNode forms = recent.path("form");
        List<FilingRef> found = new ArrayList<>();
        for (int i = 0; i < forms.size(); i++) {
            if (!"13F-HR".equals(forms.get(i).asText())) {
                continue;
            }
            LocalDate filed = LocalDate.parse(recent.path("filingDate").get(i).asText());
            String period = recent.path("reportDate").get(i).asText();
            if (filed.isBefore(DOLLAR_UNIT_SINCE) || period.isBlank()) {
                continue;
            }
            LocalDate reportPeriod = LocalDate.parse(period);
            if (found.stream().anyMatch(f -> f.reportPeriod().equals(reportPeriod))) {
                continue;   // 목록이 최신순이라 먼저 본 것이 나중에 낸 것이다
            }
            found.add(new FilingRef(recent.path("accessionNumber").get(i).asText(), reportPeriod, filed));
        }
        return found.stream()
                .sorted(Comparator.comparing(FilingRef::reportPeriod).reversed())
                .limit(quarters)
                .toList();
    }

    /** 표지가 아닌 XML 을 큰 것부터 */
    static List<String> infoTableCandidates(JsonNode index) {
        List<JsonNode> items = new ArrayList<>();
        index.path("directory").path("item").forEach(items::add);
        return items.stream()
                .filter(i -> i.path("name").asText().toLowerCase().endsWith(".xml"))
                .filter(i -> !"primary_doc.xml".equalsIgnoreCase(i.path("name").asText()))
                .sorted(Comparator.comparingLong((JsonNode i) -> i.path("size").asLong(0)).reversed())
                .map(i -> i.path("name").asText())
                .toList();
    }

    private static boolean looksLikeInfoTable(byte[] xml) {
        String head = new String(xml, 0, Math.min(xml.length, 2000), java.nio.charset.StandardCharsets.UTF_8);
        return head.contains("informationTable");
    }

    private JsonNode json(String url, String what, Object... vars) {
        if (!isConfigured()) {
            throw new AppException("SEC_USER_AGENT 가 없어 13F 를 받을 수 없습니다. \"앱이름 이메일\" 형식으로 넣으세요");
        }
        try {
            String body = edgarRestClient.get().uri(url, vars).retrieve().body(String.class);
            return objectMapper.readTree(body);
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException("SEC EDGAR " + what + " 응답을 받지 못했습니다: " + e.getMessage());
        }
    }
}
