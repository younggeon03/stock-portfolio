package com.mystock.portfolio.web;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Atom 피드 XML. 라이브러리 없이 만든다(필드 몇 개뿐이다). 대신 글자 이스케이프를 반드시 거친다.
 * 회사 이름에 & 가 흔하다(AT&T, JOHNSON & JOHNSON). 하나만 빠져도 피드 리더가 통째로 거부한다.
 */
final class AtomFeed {

    private AtomFeed() {
    }

    record Entry(String id, String title, String link, LocalDate updated, String summary) {
    }

    static String build(String feedId, String title, String selfUrl, String siteUrl, List<Entry> entries) {
        LocalDate latest = entries.stream().map(Entry::updated).max(LocalDate::compareTo).orElse(LocalDate.of(2026, 1, 1));
        StringBuilder sb = new StringBuilder(4096);
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        sb.append("<feed xmlns=\"http://www.w3.org/2005/Atom\" xml:lang=\"ko\">\n");
        sb.append("  <id>").append(esc(feedId)).append("</id>\n");
        sb.append("  <title>").append(esc(title)).append("</title>\n");
        sb.append("  <link rel=\"self\" href=\"").append(esc(selfUrl)).append("\"/>\n");
        sb.append("  <link rel=\"alternate\" href=\"").append(esc(siteUrl)).append("\"/>\n");
        sb.append("  <updated>").append(time(latest)).append("</updated>\n");
        for (Entry e : entries) {
            sb.append("  <entry>\n");
            sb.append("    <id>").append(esc(e.id())).append("</id>\n");
            sb.append("    <title>").append(esc(e.title())).append("</title>\n");
            sb.append("    <link href=\"").append(esc(e.link())).append("\"/>\n");
            sb.append("    <updated>").append(time(e.updated())).append("</updated>\n");
            sb.append("    <summary type=\"text\">").append(esc(e.summary())).append("</summary>\n");
            sb.append("  </entry>\n");
        }
        sb.append("</feed>\n");
        return sb.toString();
    }

    static String esc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&apos;");
                default -> {
                    // XML 1.0 이 허용하지 않는 제어 문자는 버린다 (줄바꿈·탭은 둔다)
                    if (c >= 0x20 || c == '\n' || c == '\r' || c == '\t') {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    private static String time(LocalDate d) {
        return d.atStartOfDay().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
