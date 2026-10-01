package com.mystock.portfolio.external.news;

import com.mystock.portfolio.external.anthropic.AnthropicProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 뉴스 인사이트의 모델 고르기.
 *
 * 하쿠에 노력 설정을 넣으면 400 이 나는데, 뉴스 요약은 실패를 조용히 삼킨다.
 * 그래서 틀려도 화면에서는 "요약이 안 나온다" 로만 보이고 원인을 알기 어렵다. 여기서 막아둔다.
 */
class NewsBriefServiceTest {

    @Test
    void 하쿠에는_노력_설정을_넣지_않는다() {
        assertThat(NewsBriefService.supportsEffort("claude-haiku-4-5-20251001")).isFalse();
        assertThat(NewsBriefService.supportsEffort("claude-opus-5")).isTrue();
    }

    @Test
    void 뉴스_모델이_비어_있으면_분석_모델을_쓴다() {
        assertThat(properties(null).newsModelOrDefault()).isEqualTo("claude-opus-5");
        assertThat(properties(" ").newsModelOrDefault()).isEqualTo("claude-opus-5");
        assertThat(properties("claude-haiku-4-5-20251001").newsModelOrDefault())
                .isEqualTo("claude-haiku-4-5-20251001");
    }

    private AnthropicProperties properties(String newsModel) {
        return new AnthropicProperties(null, "claude-opus-5", "HIGH", 16000L, 8, 10, 7, 2000, newsModel);
    }
}
