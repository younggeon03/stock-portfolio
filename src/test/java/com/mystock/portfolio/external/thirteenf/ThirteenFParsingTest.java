package com.mystock.portfolio.external.thirteenf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 13F 를 숫자로 바꾸는 부분. SEC·OpenFIGI 는 부르지 않는다.
 * XML 예시는 실제 제출 모양을 줄여 만든 것이고, 기댓값은 손으로 더했다.
 */
class ThirteenFParsingTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void 같은_종목이_운용역마다_나뉘어_있으면_합친다() throws Exception {
        List<InfoTableParser.Row> rows = parse("infotable-plain.xml");

        InfoTableParser.Row apple = rows.stream()
                .filter(r -> r.cusip().equals("037833100") && r.putCall().isEmpty()).findFirst().orElseThrow();
        assertThat(apple.shares()).isEqualTo(7500);
        assertThat(apple.valueUsd()).isEqualTo(1_500_000);
    }

    @Test
    void 풋옵션은_같은_종목이라도_주식과_따로_둔다() throws Exception {
        // 주식과 풋은 반대 방향이다. 합치면 "많이 들고 있다" 로 잘못 읽힌다
        List<InfoTableParser.Row> rows = parse("infotable-plain.xml");

        assertThat(rows).extracting(InfoTableParser.Row::cusip, InfoTableParser.Row::putCall)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("037833100", ""),
                        org.assertj.core.groups.Tuple.tuple("037833100", "PUT"),
                        org.assertj.core.groups.Tuple.tuple("02005N100", ""));
    }

    @Test
    void 콤마가_섞인_숫자와_소문자_CUSIP_도_읽는다() throws Exception {
        InfoTableParser.Row ally = parse("infotable-plain.xml").get(2);

        assertThat(ally.cusip()).isEqualTo("02005N100");
        assertThat(ally.valueUsd()).isEqualTo(1_234_567);
        assertThat(ally.shares()).isEqualTo(12_561);
    }

    @Test
    void 태그에_네임스페이스_접두사가_붙어도_읽는다() throws Exception {
        List<InfoTableParser.Row> rows = parse("infotable-prefixed.xml");

        assertThat(rows).singleElement().satisfies(r -> {
            assertThat(r.issuerName()).isEqualTo("NVIDIA CORPORATION");
            assertThat(r.valueUsd()).isEqualTo(987_654_321L);
        });
    }

    @Test
    void 비공개_처리된_빈_제출은_보유가_없다() {
        // 노르웨이 중앙은행 1·3분기의 실제 모양. 이걸 "1종목" 으로 넣으면 안 된다
        String xml = """
                <informationTable xmlns="http://www.sec.gov/edgar/document/thirteenf/informationtable">
                  <infoTable><nameOfIssuer>NA</nameOfIssuer><titleOfClass>NA</titleOfClass><cusip>000000000</cusip>
                  <value>0</value><shrsOrPrnAmt><sshPrnamt>0</sshPrnamt><sshPrnamtType>SH</sshPrnamtType></shrsOrPrnAmt>
                  </infoTable>
                </informationTable>
                """;

        assertThat(InfoTableParser.parse(new java.io.ByteArrayInputStream(xml.getBytes()))).isEmpty();
    }

    @Test
    void 금액이_천_달러_단위로_온_제출은_달러로_바꾼다() {
        // 듀케인의 실제 모양: 주당 0.05 달러로 계산된다. 천 달러 단위라는 뜻이다
        List<InfoTableParser.Row> thousands = List.of(
                new InfoTableParser.Row("67066G104", "NVIDIA", "COM", "", "SH", 1_000_000, 180_000),
                new InfoTableParser.Row("67066G104", "NVIDIA", "COM", "CALL", "SH", 10_000, 500));

        List<InfoTableParser.Row> fixed = InfoTableParser.normalizeUnits(thousands);

        assertThat(fixed).extracting(InfoTableParser.Row::valueUsd).containsExactly(180_000_000L, 500_000L);
    }

    @Test
    void 달러_단위_제출은_그대로_둔다() {
        List<InfoTableParser.Row> dollars = List.of(
                new InfoTableParser.Row("037833100", "APPLE", "COM", "", "SH", 1_000, 230_000));

        assertThat(InfoTableParser.normalizeUnits(dollars)).isSameAs(dollars);
    }

    @Test
    void 제출_목록에서_13F_원본만_최신_분기부터_고른다() throws Exception {
        JsonNode submissions = om.readTree("""
                {"filings":{"recent":{
                  "form":           ["13F-HR",     "8-K",        "13F-HR/A",   "13F-HR",     "13F-HR",     "13F-HR"],
                  "filingDate":     ["2026-08-14", "2026-08-01", "2026-06-01", "2026-05-15", "2026-02-17", "2022-11-14"],
                  "reportDate":     ["2026-06-30", "",           "2026-03-31", "2026-03-31", "2025-12-31", "2022-09-30"],
                  "accessionNumber":["acc-q2",     "acc-8k",     "acc-amend",  "acc-q1",     "acc-q4",     "acc-old"]
                }}}
                """);

        List<ThirteenFApiClient.FilingRef> picked = ThirteenFApiClient.selectFilings(submissions, 2);

        // 정정본(/A)은 빼고, 2023년 전(천 달러 단위) 것도 빼고, 최근 2분기만
        assertThat(picked).extracting(ThirteenFApiClient.FilingRef::accessionNo).containsExactly("acc-q2", "acc-q1");
        assertThat(picked.get(0).reportPeriod()).isEqualTo(LocalDate.of(2026, 6, 30));
    }

    @Test
    void 천_달러_단위_시절_제출은_받지_않는다() throws Exception {
        JsonNode submissions = om.readTree("""
                {"filings":{"recent":{"form":["13F-HR"],"filingDate":["2022-11-14"],
                 "reportDate":["2022-09-30"],"accessionNumber":["acc-old"]}}}
                """);

        assertThat(ThirteenFApiClient.selectFilings(submissions, 8)).isEmpty();
    }

    @Test
    void 파일_목록에서_표지를_빼고_큰_XML_부터_고른다() throws Exception {
        JsonNode index = om.readTree("""
                {"directory":{"item":[
                  {"name":"primary_doc.xml","size":"5555"},
                  {"name":"0001193125-26-352200-index.html","size":""},
                  {"name":"small.xml","size":"100"},
                  {"name":"56757.xml","size":"44724"}]}}
                """);

        assertThat(ThirteenFApiClient.infoTableCandidates(index)).containsExactly("56757.xml", "small.xml");
    }

    @Test
    void OpenFIGI_결과는_미국_통합시세를_고르고_못_찾은_것도_남긴다() throws Exception {
        JsonNode response = om.readTree("""
                [{"data":[{"ticker":"BRK/B","exchCode":"UN","name":"BERKSHIRE HATHAWAY INC-CL B","securityType":"Common Stock"},
                          {"ticker":"BRK/B","exchCode":"US","name":"BERKSHIRE HATHAWAY INC-CL B","securityType":"Common Stock"}]},
                 {"warning":"No identifier found."}]
                """);

        Map<String, OpenFigiClient.Figi> map = OpenFigiClient.parse(List.of("084670702", "000000000"), response);

        assertThat(map.get("084670702").ticker()).isEqualTo("BRK.B");
        assertThat(map.get("000000000").ticker()).isNull();
        assertThat(map).containsKey("000000000");   // 못 찾은 것도 남겨야 매일 다시 묻지 않는다
    }

    private List<InfoTableParser.Row> parse(String file) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/thirteenf/" + file)) {
            return InfoTableParser.parse(in);
        }
    }
}
