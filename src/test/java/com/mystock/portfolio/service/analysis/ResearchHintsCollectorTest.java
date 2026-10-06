package com.mystock.portfolio.service.analysis;

import com.mystock.portfolio.external.news.NewsItem;
import com.mystock.portfolio.service.institution.HoldingDiff;
import com.mystock.portfolio.service.institution.InstitutionPortfolioService.StockMove;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 분석 프롬프트에 넣는 기관 보유 줄과 뉴스 줄의 모양. 숫자는 데모 값 */
class ResearchHintsCollectorTest {

    private static final LocalDate Q2 = LocalDate.of(2026, 6, 30);

    private static StockMove move(String name, long shares, String weight, HoldingDiff.Kind kind, String change) {
        return new StockMove(1, name, Q2, Q2.minusMonths(3), shares, shares * 10,
                weight == null ? null : new BigDecimal(weight), kind,
                change == null ? null : new BigDecimal(change), null);
    }

    @Test
    void 요약_한_줄과_가진_기관만_비중_큰_순으로() {
        List<String> lines = ResearchHintsCollector.institutionLines(List.of(
                move("피델리티 (FMR)", 100, "3.67", HoldingDiff.Kind.ADDED, "2.16"),
                move("노르웨이 중앙은행", 50, "3.82", null, null),
                move("버크셔 해서웨이", 0, null, null, null)));

        assertThat(lines.get(0)).isEqualTo("따라가는 큰 기관 3곳 중 2곳이 들고 있다");
        assertThat(lines.get(1)).isEqualTo("피델리티 (FMR): 비중 3.67%, 앞 분기 대비 늘림 +2.2% (2026-06-30 분기말)");
        assertThat(lines.get(2)).isEqualTo("노르웨이 중앙은행: 비중 3.82%, 바로 앞 분기와 비교 불가 (2026-06-30 분기말)");
        assertThat(lines).hasSize(3);   // 안 가진 버크셔는 요약 수에만
    }

    @Test
    void 새로_산_곳은_퍼센트_없이() {
        List<String> lines = ResearchHintsCollector.institutionLines(List.of(
                move("퍼싱 스퀘어", 10, "15.26", HoldingDiff.Kind.NEW, null)));
        assertThat(lines.get(1)).isEqualTo("퍼싱 스퀘어: 비중 15.26%, 앞 분기 대비 새로 삼 (2026-06-30 분기말)");
    }

    @Test
    void 뉴스_줄은_제목과_날짜() {
        assertThat(ResearchHintsCollector.line(new NewsItem("제목", "http://x", "Mon, 05 Oct 2026"))).isEqualTo("제목 (Mon, 05 Oct 2026)");
        assertThat(ResearchHintsCollector.line(new NewsItem("제목", "http://x", null))).isEqualTo("제목");
    }
}
