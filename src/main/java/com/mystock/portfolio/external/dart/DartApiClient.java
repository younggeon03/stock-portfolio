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
     * 공시 검색. 배당결정처럼 거래소 공시(pblntf_ty=I)를 기간으로 찾는다. 한 번에 100건까지.
     * 날짜 형식은 yyyyMMdd.
     */
    public List<DartDisclosureRow> disclosures(String corpCode, java.time.LocalDate from, java.time.LocalDate to,
                                               String type) {
        requireKey();
        java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.BASIC_ISO_DATE;
        DartDisclosureRow.Response response = dartRestClient.get()
                .uri(uri -> uri.path("/api/list.json")
                        .queryParam("crtfc_key", properties.apiKey())
                        .queryParam("corp_code", corpCode)
                        .queryParam("bgn_de", from.format(f))
                        .queryParam("end_de", to.format(f))
                        .queryParam("pblntf_ty", type)
                        .queryParam("page_count", 100)
                        .build())
                .retrieve()
                .body(DartDisclosureRow.Response.class);
        if (response == null || STATUS_NO_DATA.equals(response.status())) {
            return List.of();
        }
        if (!"000".equals(response.status())) {
            throw new AppException("DART 오류 " + response.status() + ": " + response.message());
        }
        return response.list() == null ? List.of() : response.list();
    }

    /**
     * 공시 본문을 태그를 걷어낸 글자로. ZIP 안의 XML(HTML 비슷한 문서) 한 개다.
     * 오래된 공시는 EUC-KR 이라 문서 머리의 encoding 을 보고 읽는다.
     */
    public String documentText(String receiptNo) {
        requireKey();
        byte[] body = dartRestClient.get()
                .uri(uri -> uri.path("/api/document.xml")
                        .queryParam("crtfc_key", properties.apiKey())
                        .queryParam("rcept_no", receiptNo)
                        .build())
                .retrieve()
                .body(byte[].class);
        if (body == null || body.length < 2 || body[0] != 'P' || body[1] != 'K') {
            throw new AppException("DART 공시 본문을 받지 못했습니다: " + receiptNo);
        }
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(body))) {
            ZipEntry entry = zip.getNextEntry();
            if (entry == null) {
                throw new AppException("DART 공시 본문 ZIP 이 비어 있습니다: " + receiptNo);
            }
            return flatten(decode(zip.readAllBytes()));
        } catch (IOException e) {
            throw new AppException("DART 공시 본문을 읽지 못했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * 공시 문서의 글자 인코딩을 정한다.
     *
     * 문서 머리의 선언을 믿으면 안 된다. 2026년 공시도 머리에는 charset=euc-kr 이라고 적혀 있는데
     * 실제 내용은 UTF-8 이다. 선언대로 EUC-KR 로 읽었더니 글자가 다 깨져 배당 공시가 하나도 안 읽혔다.
     * 그래서 UTF-8 로 엄격하게 읽어 보고, UTF-8 로 맞지 않는 바이트가 있을 때만 EUC-KR 로 읽는다.
     */
    static String decode(byte[] bytes) {
        try {
            return java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes))
                    .toString();
        } catch (java.nio.charset.CharacterCodingException e) {
            return new String(bytes, java.nio.charset.Charset.forName("EUC-KR"));
        }
    }

    /**
     * 태그를 걷어내고 공백을 하나로. 표의 "칸 이름 값" 이 한 줄로 이어진다.
     *
     * 공시 문서에는 줄바꿈 없는 공백(NBSP, U+00A0)이 섞여 있다. 자바의 \s 는 이걸 공백으로 안 본다
     * (자바스크립트는 본다). 그래서 처음엔 실제 공시가 하나도 안 읽혔다. 유니코드 공백을 전부 보통 공백으로 바꾼다.
     */
    static String flatten(String html) {
        return html.replaceAll("(?s)<[^>]+>", " ")
                .replace("&nbsp;", " ").replace("&#160;", " ")
                .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replaceAll("(?U)\\s+", " ")
                .replace(' ', ' ')
                .replaceAll(" +", " ")
                .strip();
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
