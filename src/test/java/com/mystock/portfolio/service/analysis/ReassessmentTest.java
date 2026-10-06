package com.mystock.portfolio.service.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mystock.portfolio.domain.CompanyAnalysis;
import com.mystock.portfolio.external.anthropic.AnthropicProperties;
import com.mystock.portfolio.external.anthropic.ClaudeAnalysisClient;
import com.mystock.portfolio.external.filing.FilingService;
import com.mystock.portfolio.external.toss.TossMarketDataService;
import com.mystock.portfolio.service.TossAnalysisService;
import com.mystock.portfolio.service.UnifiedPortfolioService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** "판단만 새로": 조사 섹션은 그대로, 판정·요약·내 위치·위험만 바뀐다. 숫자는 데모 값 */
class ReassessmentTest {

    private static final String STORED = """
            {"symbol":"MSFT","name":"마이크로소프트","instrumentType":"STOCK","oneLineSummary":"옛 요약",
             "verdict":{"stance":"HOLD","headline":"기다릴 자리","reason":"옛 이유","basis":["PRICE"]},
             "sections":[
               {"key":"POSITION_REVIEW","title":"현재가 vs 내 평단가","applicable":true,"notApplicableReason":"",
                "body":"옛 위치","bullets":["옛 관점"],"metrics":[],"sources":[]},
               {"key":"VALUATION_METRICS","title":"수치 지표","applicable":true,"notApplicableReason":"",
                "body":"적정가는 400~450달러다.","bullets":[],
                "metrics":[{"label":"적정가 (2026년 이익 기준)","note":"","points":[{"period":"2026","periodType":"POINT","value":"$400~450","sourceIndex":0}]}],
                "sources":[{"title":"리포트","url":"https://web.example","publisher":"증권사","publishedAt":""}]}],
             "risks":[{"title":"옛 위험","detail":"옛","severity":"LOW"}]}""";

    private static final String SUBMITTED = """
            {"oneLineSummary":"새 요약",
             "verdict":{"stance":"ADD","headline":"담을 자리","reason":"현재가 380달러는 적정가 아래","basis":["VALUATION"]},
             "positionReview":{"applicable":true,"notApplicableReason":"","body":"새 위치","bullets":["새 관점"]},
             "risks":[{"title":"새 위험","detail":"새","severity":"MEDIUM"}]}""";

    private final ObjectMapper mapper = new ObjectMapper();
    private final CompanyAnalysisStore store = mock(CompanyAnalysisStore.class);
    private final AnthropicProperties props = mock(AnthropicProperties.class);
    private final CompanyAnalysisService service = new CompanyAnalysisService(
            mock(UnifiedPortfolioService.class), mock(TossMarketDataService.class), mock(TossAnalysisService.class),
            mock(ClaudeAnalysisClient.class), new CompanyAnalysisPromptBuilder(), store,
            props, mapper, mock(FilingService.class), mock(ResearchHintsCollector.class));

    @Test
    void 조사_섹션은_그대로_두고_판단만_바꾼다() throws Exception {
        CompanyAnalysisView stored = mapper.readValue(STORED, CompanyAnalysisView.class);

        CompanyAnalysisView merged = mapper.readValue(service.mergeReassessment(stored, SUBMITTED), CompanyAnalysisView.class);

        assertThat(merged.oneLineSummary()).isEqualTo("새 요약");
        assertThat(merged.verdict().stance()).isEqualTo("ADD");
        assertThat(merged.risks()).extracting(CompanyAnalysisView.Risk::title).containsExactly("새 위험");
        assertThat(merged.sections().get(0).body()).isEqualTo("새 위치");
        assertThat(merged.sections().get(0).bullets()).containsExactly("새 관점");
        assertThat(merged.sections().get(1)).isEqualTo(stored.sections().get(1));   // 조사는 한 글자도 안 바뀜
    }

    @Test
    void 모델이_칸을_비우면_옛_값을_남긴다() throws Exception {
        CompanyAnalysisView stored = mapper.readValue(STORED, CompanyAnalysisView.class);
        Reassessment empty = new Reassessment("", null, null, List.of());

        CompanyAnalysisView merged = empty.mergeInto(stored);

        assertThat(merged.oneLineSummary()).isEqualTo("옛 요약");
        assertThat(merged.verdict()).isEqualTo(stored.verdict());
        assertThat(merged.risks()).isEqualTo(stored.risks());
        assertThat(merged.sections()).isEqualTo(stored.sections());
    }

    @Test
    void 조사가_보관_일수를_넘겼으면_받지_않는다() {
        when(props.cacheDays()).thenReturn(7);
        CompanyAnalysis entity = new CompanyAnalysis("MSFT", "마이크로소프트");
        entity.markSuccess(STORED, "claude", 1, 1, 0, true);
        ReflectionTestUtils.setField(entity, "researchedAt", LocalDateTime.now().minusDays(9));
        when(store.find("MSFT")).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.reassess("MSFT", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("전체 다시 분석");
    }

    @Test
    void 분석이_없으면_받지_않는다() {
        when(store.find("MSFT")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.reassess("MSFT", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 판단만_새로_해도_조사_시각은_남는다() {
        CompanyAnalysis entity = new CompanyAnalysis("MSFT", "마이크로소프트");
        entity.markSuccess(STORED, "claude", 1, 1, 3, false);
        LocalDateTime researched = LocalDateTime.now().minusDays(2);
        ReflectionTestUtils.setField(entity, "researchedAt", researched);

        entity.markReassessed(STORED, "claude", 1, 1, true);

        assertThat(entity.getResearchedAt()).isEqualTo(researched);
        assertThat(entity.getAnalyzedAt()).isAfter(researched);
        assertThat(entity.getWebSearchCount()).isZero();
        assertThat(entity.isIncludesPosition()).isTrue();
    }

    @Test
    void 판단용_지시문은_판정_규칙만_뽑고_섹션_규칙은_뺀다() {
        String prompt = new CompanyAnalysisPromptBuilder().reassessSystemPrompt();

        assertThat(prompt).contains("판단만 새로 쓰는 것");
        assertThat(prompt).contains("## 판정(verdict) 쓰는 법");
        assertThat(prompt).contains("## 보유하지 않은 종목");
        assertThat(prompt).contains("모든 섹션은 두괄식이다");
        assertThat(prompt).doesNotContain("## 섹션별로 무엇을 쓰는가");
        assertThat(prompt).doesNotContain("## 지표(metrics) 쓰는 법");
    }
}
