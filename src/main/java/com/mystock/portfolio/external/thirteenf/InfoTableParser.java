package com.mystock.portfolio.external.thirteenf;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 13F 의 보유 표(information table XML)를 읽는다.
 *
 * ★ 원본 그대로 쓰면 안 되는 이유 둘
 * 1. 같은 종목이 여러 줄이다. 버크셔는 애플 한 종목이 운용역(otherManager)별로 나뉘어 열 줄 가까이 나온다.
 *    그대로 세면 종목 수가 부풀고 비중이 쪼개진다. CUSIP·풋콜 단위로 합친다.
 * 2. 태그에 네임스페이스 접두사가 붙기도 안 붙기도 한다(ns1:infoTable / infoTable).
 *    제출 대행사마다 다르다. 그래서 접두사를 떼고 이름만 본다.
 *
 * DOM 대신 StAX 로 읽는다. 노르웨이 중앙은행은 표 하나가 수십 MB 다.
 */
public final class InfoTableParser {

    private InfoTableParser() {
    }

    /** 합친 보유 한 줄 */
    public record Row(String cusip, String issuerName, String titleOfClass, String putCall,
                      String shareType, long shares, long valueUsd) {
    }

    public static List<Row> parse(InputStream in) {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        // 외부 엔티티를 막는다. 남이 만든 XML 을 읽으므로 XXE 공격 경로를 닫아 둔다
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);

        Map<String, Row> merged = new LinkedHashMap<>();
        try {
            XMLStreamReader r = factory.createXMLStreamReader(in);
            Map<String, String> current = null;
            String field = null;
            StringBuilder text = new StringBuilder();

            while (r.hasNext()) {
                int event = r.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String name = r.getLocalName();
                    if ("infoTable".equals(name)) {
                        current = new LinkedHashMap<>();
                    } else if (current != null) {
                        field = name;
                        text.setLength(0);
                    }
                } else if (event == XMLStreamConstants.CHARACTERS && field != null) {
                    text.append(r.getText());
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    String name = r.getLocalName();
                    if ("infoTable".equals(name) && current != null) {
                        add(merged, current);
                        current = null;
                    } else if (current != null && name.equals(field)) {
                        current.put(field, text.toString().strip());
                        field = null;
                    }
                }
            }
        } catch (XMLStreamException e) {
            throw new IllegalStateException("13F 보유 표를 읽지 못했습니다: " + e.getMessage(), e);
        }
        return new ArrayList<>(merged.values());
    }

    /**
     * 금액이 천 달러 단위로 온 제출이면 달러로 바꾼다.
     *
     * 2023년부터 13F 금액은 달러가 규칙인데 아직 천 달러로 내는 곳이 있다(듀케인 패밀리 오피스,
     * 2026년에도). 그대로 두면 비중은 맞아도 합계와 기관끼리의 비교가 천 배 틀린다.
     * 표에 단위가 안 적혀 있어서 주식 줄의 "금액 ÷ 주식 수" 로 평균 단가를 거꾸로 구해 판단한다.
     * 평균 단가가 1달러 미만인 포트폴리오는 현실에 거의 없고, 천 달러 단위면 수십~수백 달러 주식이
     * 0.0x 달러로 보인다.
     */
    public static List<Row> normalizeUnits(List<Row> rows) {
        long value = 0;
        long shares = 0;
        for (Row r : rows) {
            if (r.putCall().isEmpty() && "SH".equals(r.shareType())) {
                value += r.valueUsd();
                shares += r.shares();
            }
        }
        if (shares == 0 || (double) value / shares >= 1.0) {
            return rows;
        }
        return rows.stream()
                .map(r -> new Row(r.cusip(), r.issuerName(), r.titleOfClass(), r.putCall(), r.shareType(),
                        r.shares(), r.valueUsd() * 1000))
                .toList();
    }

    private static void add(Map<String, Row> merged, Map<String, String> f) {
        String cusip = upper(f.get("cusip"));
        // 비공개 처리된 제출은 "NA / 000000000 / 0" 한 줄짜리 껍데기다 (노르웨이 중앙은행의 1·3분기).
        // 그대로 넣으면 "보유 1종목" 으로 보인다
        if (cusip == null || cusip.isBlank() || cusip.chars().allMatch(c -> c == '0')) {
            return;
        }
        String putCall = upper(f.getOrDefault("putCall", ""));
        long shares = number(f.get("sshPrnamt"));
        long value = number(f.get("value"));
        String key = cusip + "|" + putCall;

        Row before = merged.get(key);
        if (before == null) {
            merged.put(key, new Row(cusip, f.getOrDefault("nameOfIssuer", ""), f.get("titleOfClass"),
                    putCall, f.get("sshPrnamtType"), shares, value));
        } else {
            merged.put(key, new Row(cusip, before.issuerName(), before.titleOfClass(), putCall,
                    before.shareType(), before.shares() + shares, before.valueUsd() + value));
        }
    }

    private static String upper(String s) {
        return s == null ? null : s.strip().toUpperCase();
    }

    /** "1,234" 처럼 콤마가 섞인 제출도 있다. 소수점 이하는 버린다(주식 수·달러라 소수가 의미 없다) */
    private static long number(String s) {
        if (s == null || s.isBlank()) {
            return 0;
        }
        String digits = s.replace(",", "").strip();
        int dot = digits.indexOf('.');
        if (dot >= 0) {
            digits = digits.substring(0, dot);
        }
        return digits.isEmpty() ? 0 : Long.parseLong(digits);
    }
}
