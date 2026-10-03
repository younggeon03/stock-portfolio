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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 공개 기업분석의 선. 평단가가 들어간 분석은 공개 주소로 절대 나가면 안 된다(공개 저장소·공개 화면 규칙).
 * "있다" 는 사실도 숨긴다. 비공개 분석이 있다고 알리면 내가 그 종목을 가졌다는 정보가 샌다.
 */
class CompanyAnalysisPublicTest {

    private static final String JSON = """
            {"symbol":"MSFT","name":"마이크로소프트","instrumentType":"STOCK","oneLineSummary":"요약",
             "verdict":{"stance":"HOLD","headline":"기다릴 자리","reason":"현재가와 적정가","basis":[]},
             "sections":[],"risks":[]}""";

    private final CompanyAnalysisStore store = mock(CompanyAnalysisStore.class);
    private final CompanyAnalysisService service = new CompanyAnalysisService(
            mock(UnifiedPortfolioService.class), mock(TossMarketDataService.class), mock(TossAnalysisService.class),
            mock(ClaudeAnalysisClient.class), new CompanyAnalysisPromptBuilder(), store,
            mock(AnthropicProperties.class), new ObjectMapper(), mock(FilingService.class));

    private CompanyAnalysis saved(boolean includesPosition) {
        CompanyAnalysis entity = new CompanyAnalysis("MSFT", "마이크로소프트");
        entity.markSuccess(JSON, "claude", 1, 1, 0, includesPosition);
        return entity;
    }

    @Test
    void 평단가가_들어간_분석은_공개하지_않고_있다는_것도_숨긴다() {
        when(store.find("MSFT")).thenReturn(Optional.of(saved(true)));

        CompanyAnalysisResponse r = service.findPublic("MSFT");

        assertThat(r.status()).isEqualTo("NONE");
        assertThat(r.analysis()).isNull();
        assertThat(r.analyzedAt()).isNull();
    }

    @Test
    void 현재가만_본_분석은_공개한다() {
        when(store.find("MSFT")).thenReturn(Optional.of(saved(false)));

        CompanyAnalysisResponse r = service.findPublic("MSFT");

        assertThat(r.status()).isEqualTo("OK");
        assertThat(r.analysis()).isNotNull();
        assertThat(r.includesPosition()).isFalse();
        assertThat(r.lastError()).isNull();   // 실패 메시지는 공개 화면에 안 보낸다
    }

    @Test
    void 로그인한_나에게는_평단가_분석도_그대로() {
        when(store.find("MSFT")).thenReturn(Optional.of(saved(true)));

        CompanyAnalysisResponse r = service.find("MSFT");

        assertThat(r.analysis()).isNotNull();
        assertThat(r.includesPosition()).isTrue();
    }
}
