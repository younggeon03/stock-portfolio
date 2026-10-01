package com.mystock.portfolio.external.dart;

import com.mystock.portfolio.common.AppException;
import com.mystock.portfolio.domain.DartCorpCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * DART OpenAPI 호출만 담당한다. 해석은 {@link DartFinancialService} 가 한다.
 *
 * DART 는 오류도 HTTP 200 으로 주고 본문의 status 로 알린다. 그래서 status 를 직접 봐야 한다.
 * 000 정상, 013 데이터 없음(아직 공시 전), 010·011 키 문제, 020 호출 한도 초과.
 */
@Component
public class DartApiClient {

    /** 조회된 데이터가 없음. 오류가 아니라 "아직 그 보고서가 안 나왔다" 는 뜻이다 */
    static final String STATUS_NO_DATA = "013";

    private final RestClient dartRestClient;
    private final DartProperties properties;

    public DartApiClient(RestClient dartRestClient, DartProperties properties) {
        this.dartRestClient = dartRestClient;
        this.properties = properties;
    }

    /**
     * 단일회사 주요계정. 한 번에 당기·전기·전전기 세 해가 같이 온다.
     *
     * @param reportCode 11011 사업 / 11012 반기 / 11013 1분기 / 11014 3분기
     * @return 그 보고서가 아직 없으면 빈 목록
     */
    public List<DartAccountRow> singleAccounts(String corpCode, int year, String reportCode) {
        requireKey();
        DartAccountRow.Response response = dartRestClient.get()
                .uri(uri -> uri.path("/api/fnlttSinglAcnt.json")
                        .queryParam("crtfc_key", properties.apiKey())
                        .queryParam("corp_code", corpCode)
                        .queryParam("bsns_year", year)
                        .queryParam("reprt_code", reportCode)
                        .build())
                .retrieve()
                .body(DartAccountRow.Response.class);

        if (response == null) {
            throw new AppException("DART 응답이 비어 있습니다.");
        }
        if (STATUS_NO_DATA.equals(response.status())) {
            return List.of();
        }
        if (!"000".equals(response.status())) {
            throw new AppException("DART 오류 " + response.status() + ": " + response.message());
        }
        return response.list() == null ? List.of() : response.list();
    }

    /**
     * 전체 재무제표. 지배주주 순이익·자본을 꺼내는 데만 쓴다.
     *
     * @param fsDiv CFS 연결 / OFS 별도. 요약과 달리 한 번에 하나만 준다
     * @return 그 보고서가 아직 없으면 빈 목록
     */
    public List<DartOwnerRow> fullAccounts(String corpCode, int year, String reportCode, String fsDiv) {
        requireKey();
        DartOwnerRow.Response response = dartRestClient.get()
                .uri(uri -> uri.path("/api/fnlttSinglAcntAll.json")
                        .queryParam("crtfc_key", properties.apiKey())
                        .queryParam("corp_code", corpCode)
                        .queryParam("bsns_year", year)
                        .queryParam("reprt_code", reportCode)
                        .queryParam("fs_div", fsDiv)
                        .build())
                .retrieve()
                .body(DartOwnerRow.Response.class);

        if (response == null || STATUS_NO_DATA.equals(response.status())) {
            return List.of();
        }
        if (!"000".equals(response.status())) {
            throw new AppException("DART 오류 " + response.status() + ": " + response.message());
        }
        return response.list() == null ? List.of() : response.list();
    }

    /**
     * 주식의 총수 현황. 재무와 같은 보고서에서 가져와야 기준일이 맞는다.
     *
     * @return 그 보고서가 아직 없으면 빈 목록
     */
    public List<DartShareRow> shareCounts(String corpCode, int year, String reportCode) {
        requireKey();
        DartShareRow.Response response = dartRestClient.get()
                .uri(uri -> uri.path("/api/stockTotqySttus.json")
                        .queryParam("crtfc_key", properties.apiKey())
                        .queryParam("corp_code", corpCode)
                        .queryParam("bsns_year", year)
                        .queryParam("reprt_code", reportCode)
                        .build())
                .retrieve()
                .body(DartShareRow.Response.class);

        if (response == null || STATUS_NO_DATA.equals(response.status())) {
            return List.of();
        }
        if (!"000".equals(response.status())) {
            throw new AppException("DART 오류 " + response.status() + ": " + response.message());
        }
        return response.list() == null ? List.of() : response.list();
    }

    /**
     * 고유번호 파일을 받아 상장사만 골라낸다.
     *
     * 성공하면 ZIP 이 오고, 실패하면(키 오류 등) ZIP 이 아니라 XML 오류문이 온다.
     * 그래서 첫 두 바이트가 ZIP 서명(PK)인지 먼저 본다.
     */
    public List<DartCorpCode> listedCorpCodes() {
        requireKey();
        byte[] body = dartRestClient.get()
                .uri(uri -> uri.path("/api/corpCode.xml")
                        .queryParam("crtfc_key", properties.apiKey())
                        .build())
                .retrieve()
                .body(byte[].class);

        if (body == null || body.length < 2 || body[0] != 'P' || body[1] != 'K') {
            String text = body == null ? "" : new String(body, 0, Math.min(body.length, 300),
                    java.nio.charset.StandardCharsets.UTF_8);
            throw new AppException("DART 고유번호 파일을 받지 못했습니다: " + text);
        }

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(body))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().toLowerCase().endsWith(".xml")) {
                    return parseListed(zip);
                }
            }
        } catch (IOException | XMLStreamException e) {
            throw new AppException("DART 고유번호 파일을 읽지 못했습니다: " + e.getMessage(), e);
        }
        throw new AppException("DART 고유번호 ZIP 안에 XML 이 없습니다.");
    }

    /**
     * 30MB XML 을 한 번에 트리로 올리지 않고 흘려 읽는다.
     * 종목코드가 빈 항목(비상장사)이 대부분이라 버리고, 상장사 4천 건 정도만 남긴다.
     */
    static List<DartCorpCode> parseListed(InputStream xml) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        // 외부 엔티티를 막는다. 받는 파일이지만 XML 파서 기본값을 믿지 않는다
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);

        XMLStreamReader reader = factory.createXMLStreamReader(xml, "UTF-8");
        List<DartCorpCode> result = new ArrayList<>();
        String corpCode = null;
        String corpName = null;
        String stockCode = null;

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                // getElementText 로 통째로 읽는다. "&amp;" 가 낀 회사명은 글자 조각이 여러 번 나뉘어 온다
                switch (reader.getLocalName()) {
                    case "list" -> corpCode = corpName = stockCode = null;
                    case "corp_code" -> corpCode = reader.getElementText().strip();
                    case "corp_name" -> corpName = reader.getElementText().strip();
                    case "stock_code" -> stockCode = reader.getElementText().strip();
                    default -> { }
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && "list".equals(reader.getLocalName())
                    && stockCode != null && stockCode.length() == 6 && corpCode != null) {
                result.add(new DartCorpCode(stockCode, corpCode,
                        corpName == null || corpName.isEmpty() ? stockCode : corpName));
            }
        }
        return result;
    }

    private void requireKey() {
        if (!properties.hasKey()) {
            throw new AppException("DART 인증키가 없습니다. .env 에 DART_API_KEY 를 넣어주세요.");
        }
    }
}
