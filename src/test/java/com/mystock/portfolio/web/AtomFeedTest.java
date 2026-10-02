package com.mystock.portfolio.web;

import com.mystock.portfolio.external.telegram.TelegramNotifierTestAccess;
import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Atom 피드가 피드 리더가 읽을 수 있는 XML 인지, 텔레그램이 꺼져 있을 때 조용한지 */
class AtomFeedTest {

    @Test
    void 회사_이름의_앰퍼샌드와_꺾쇠가_있어도_올바른_XML_이다() throws Exception {
        String xml = AtomFeed.build("tag:test", "피드", "http://x/feeds/13f.xml", "http://x/",
                List.of(new AtomFeed.Entry("tag:test:1", "AT&T <신규>", "http://x/a?cik=1&period=2026-06-30",
                        LocalDate.of(2026, 8, 14), "JOHNSON & JOHNSON \"늘림\"\n줄임: 'KR'")));

        // 파싱이 되면 피드 리더도 읽는다. 하나라도 이스케이프가 빠지면 여기서 예외
        var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        assertThat(doc.getElementsByTagName("entry").getLength()).isEqualTo(1);
        assertThat(doc.getElementsByTagName("title").item(1).getTextContent()).isEqualTo("AT&T <신규>");
        assertThat(xml).contains("<updated>2026-08-14T00:00:00Z</updated>");
    }

    @Test
    void XML_에_못_넣는_제어_문자는_버린다() {
        assertThat(AtomFeed.esc("A\u0001B\nC")).isEqualTo("AB\nC");
    }

    @Test
    void 텔레그램은_토큰과_채널이_둘_다_있어야_켜지고_꺼져_있으면_보내지_않는다() {
        assertThat(TelegramNotifierTestAccess.enabled("", "@ch")).isFalse();
        assertThat(TelegramNotifierTestAccess.enabled("token", "")).isFalse();
        assertThat(TelegramNotifierTestAccess.sendWhileDisabled()).isFalse();
        assertThat(TelegramNotifierTestAccess.truncate("가".repeat(5000))).hasSize(4096);
    }
}
