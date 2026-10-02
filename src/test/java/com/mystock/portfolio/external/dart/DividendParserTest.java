package com.mystock.portfolio.external.dart;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 배당결정 공시 읽기. dividend-samsung-2026q2.txt 는 2026-07-30 삼성전자 분기배당 공시 본문을
 * 태그만 걷어낸 것이다(공개 자료). 기댓값은 원문을 눈으로 읽어 적었다.
 */
class DividendParserTest {

    @Test
    void 실제_분기배당_공시를_읽는다() throws Exception {
        DividendParser.Dividend d = DividendParser.parse(fixture("dividend-samsung-2026q2.txt")).orElseThrow();

        assertThat(d.kind()).isEqualTo("분기배당");
        assertThat(d.cashType()).isEqualTo("현금배당");
        assertThat(d.perShareCommon()).isEqualByComparingTo("374");
        assertThat(d.perSharePreferred()).isEqualByComparingTo("374");
        assertThat(d.yieldCommon()).isEqualByComparingTo("0.1");
        assertThat(d.recordDate()).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(d.payDate()).isEqualTo(LocalDate.of(2026, 8, 28));
        assertThat(d.boardDate()).isEqualTo(LocalDate.of(2026, 7, 30));
    }

    @Test
    void 주총_전_결산배당은_지급일이_없고_우선주가_없으면_비워_둔다() {
        String text = "1. 배당구분 결산배당 2. 배당종류 현금배당 3. 1주당 배당금(원) 보통주식 1,668 종류주식 - "
                + "4. 시가배당률(%) 보통주식 1.5 종류주식 - 6. 배당기준일 2025-12-31 7. 배당금지급 예정일자 - "
                + "10. 이사회결의일(결정일) 2026-01-29";

        DividendParser.Dividend d = DividendParser.parse(text).orElseThrow();

        assertThat(d.kind()).isEqualTo("결산배당");
        assertThat(d.perShareCommon()).isEqualByComparingTo("1668");
        assertThat(d.perSharePreferred()).isNull();
        assertThat(d.payDate()).isNull();   // 지어내지 않는다. 화면은 "미정"
    }

    @Test
    void 날짜가_년월일로_적혀도_읽는다() {
        String text = "배당구분 중간배당 1주당 배당금(원) 보통주식 500 배당기준일 2026년 06월 30일 "
                + "배당금지급 예정일자 2026.08.20";

        DividendParser.Dividend d = DividendParser.parse(text).orElseThrow();

        assertThat(d.recordDate()).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(d.payDate()).isEqualTo(LocalDate.of(2026, 8, 20));
    }

    @Test
    void 배당결정_공시가_아니면_비어_있다() {
        assertThat(DividendParser.parse("주요사항보고서 유상증자결정 신주의 종류와 수")).isEmpty();
        assertThat(DividendParser.parse(null)).isEmpty();
    }

    @Test
    void 줄바꿈_없는_공백이_섞인_실제_문서도_읽는다() {
        // 실제 공시 HTML 의 모양. 칸 사이에 NBSP 가 있다. 자바 \s 는 이걸 못 잡아 처음엔 하나도 안 읽혔다
        String html = "<td>1. 배당구분</td><td>분기배당</td><td>3. 1주당 배당금(원)</td><td>보통주식</td>"
                + "<td>374</td><td>6. 배당기준일</td><td>2026-06-30&nbsp;</td><td>7. 배당금지급 예정일자</td>"
                + "<td>&#160;2026-08-28</td>";

        DividendParser.Dividend d = DividendParser.parse(DartApiClient.flatten(html)).orElseThrow();

        assertThat(d.recordDate()).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(d.payDate()).isEqualTo(LocalDate.of(2026, 8, 28));
        assertThat(d.perShareCommon()).isEqualByComparingTo("374");
    }

    @Test
    void 머리에_euc_kr_이라고_적혀_있어도_내용이_UTF8_이면_UTF8_로_읽는다() {
        // 실제 2026년 공시의 모양. 선언을 믿고 EUC-KR 로 읽었다가 배당 공시가 하나도 안 읽혔다
        byte[] utf8 = "<meta content=\"text/html; charset=euc-kr\">6. 배당기준일 2026-06-30"
                .getBytes(StandardCharsets.UTF_8);

        assertThat(DartApiClient.decode(utf8)).contains("배당기준일");
    }

    @Test
    void 진짜_EUC_KR_문서는_EUC_KR_로_읽는다() {
        byte[] eucKr = "6. 배당기준일 2010-12-31".getBytes(java.nio.charset.Charset.forName("EUC-KR"));

        assertThat(DartApiClient.decode(eucKr)).contains("배당기준일");
    }

    @Test
    void 공시_본문의_태그를_걷어내고_공백을_하나로() {
        assertThat(DartApiClient.flatten("<td><span>6. 배당기준일</span></td>\n<td> 2026-06-30 &amp; </td>"))
                .isEqualTo("6. 배당기준일 2026-06-30 &");
    }

    private String fixture(String name) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/dart/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
