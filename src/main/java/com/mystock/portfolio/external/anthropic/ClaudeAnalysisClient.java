package com.mystock.portfolio.external.anthropic;

import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import com.anthropic.models.messages.ToolUnion;
import com.anthropic.models.messages.WebSearchTool20260209;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mystock.portfolio.common.AppException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 클로드에게 기업분석을 시키는 창구.
 *
 * 클라이언트 만들기·에러 번역·도구 스키마 읽기는 AnthropicClientProvider 가 맡고,
 * 이 클래스는 "기업분석을 어떻게 요청할지" 에만 집중한다.
 */
@Component
public class ClaudeAnalysisClient {

    private static final Logger log = LoggerFactory.getLogger(ClaudeAnalysisClient.class);

    /** 분석 결과를 제출받을 도구 이름 */
    private static final String SUBMIT_TOOL = "submit_analysis";

    /** 웹검색이 길어져 턴이 끊길 때 이어서 호출할 최대 횟수 */
    private static final int MAX_CONTINUE = 6;

    private final AnthropicClientProvider provider;
    private final AnthropicProperties properties;
    private final ObjectMapper objectMapper;

    /** 도구 스키마는 한 번만 읽어서 재사용한다 */
    private volatile ToolUnion submitTool;

    public ClaudeAnalysisClient(AnthropicClientProvider provider,
                                AnthropicProperties properties,
                                ObjectMapper objectMapper) {
        this.provider = provider;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 기업분석을 요청한다.
     *
     * @param systemPrompt 역할과 규칙 (고정)
     * @param userPrompt   종목별 확정 정보와 요청 (매번 다름)
     */
    public ClaudeCallResult analyze(String systemPrompt, String userPrompt) {
        long startedAt = System.currentTimeMillis();

        Message message = call(baseParams(systemPrompt, userPrompt).build());

        int inputTokens = 0;
        int cacheWriteTokens = 0;
        int cacheReadTokens = 0;
        int outputTokens = 0;
        int webSearchCount = 0;
        int continued = 0;

        // 웹검색이 길어지면 stop_reason 이 pause_turn 으로 끊겨 돌아온다.
        // 그때는 지금까지의 대화를 그대로 이어붙여 다시 호출한다.
        while (true) {
            inputTokens += (int) message.usage().inputTokens();
            cacheWriteTokens += message.usage().cacheCreationInputTokens().orElse(0L).intValue();
            cacheReadTokens += message.usage().cacheReadInputTokens().orElse(0L).intValue();
            outputTokens += (int) message.usage().outputTokens();
            webSearchCount += countWebSearches(message);

            String submitted = findSubmittedAnalysis(message);
            if (submitted != null) {
                long elapsed = (System.currentTimeMillis() - startedAt) / 1000;
                ClaudeCallResult result = new ClaudeCallResult(submitted, properties.model(),
                        inputTokens, cacheWriteTokens, cacheReadTokens,
                        outputTokens, webSearchCount, elapsed);
                // 캐시 읽기가 0 이면 캐싱이 죽은 것이다. 에러가 안 나고 요금만 올라가므로 매번 찍어서 본다.
                log.info("기업분석 완료 - 프롬프트 {}토큰(제값 {} / 캐시쓰기 {} / 캐시읽기 {}) "
                                + "/ 출력 {}토큰 / 웹검색 {}회 / 소요 {}초 / 약 ${}",
                        result.totalPromptTokens(), inputTokens, cacheWriteTokens, cacheReadTokens,
                        outputTokens, webSearchCount, elapsed,
                        String.format("%.3f", result.estimatedUsd()));
                return result;
            }

            if (!isPaused(message)) {
                throw new AppException("클로드가 분석 결과를 규격대로 제출하지 않았습니다. 다시 시도해 주세요.");
            }

            if (++continued > MAX_CONTINUE) {
                throw new AppException("분석이 너무 오래 걸려 중단했습니다. 웹검색 횟수를 줄이고 다시 시도해 보세요.");
            }

            log.info("웹검색이 길어져 이어서 호출합니다 ({}회차)", continued);
            // 직전 응답을 그대로 대화에 붙이고 다시 부른다. 그래야 이미 검색한 내용을 다시 찾지 않는다.
            message = call(baseParams(systemPrompt, userPrompt)
                    .addMessage(message)
                    .build());
        }
    }

    /** 실제 호출 + 에러 번역 */
    private Message call(MessageCreateParams params) {
        try {
            return provider.client().messages().create(params);
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException(provider.describeError(e), e);
        }
    }

    /**
     * 매번 똑같이 들어가는 호출 설정.
     * 첫 호출과 이어서 호출할 때 모두 이걸 쓴다.
     *
     * ★ 캐싱을 여기서 건다. 분석 한 번에 최대 7번 호출하는데, 그때마다 앞부분이 똑같이 다시 나간다.
     *
     * 프롬프트는 tools → system → messages 순으로 조립된다. 캐시는 앞에서부터 글자가 똑같은 구간까지만 듣는다.
     * 그래서 표시는 두 군데에만 붙인다.
     *
     * 1. system 블록에 1시간 표시
     *    지시문(9천자) + 제출 스키마(9천자) 를 합쳐 1만 토큰쯤 되고, 이건 종목이 바뀌어도 한 글자도 안 변한다.
     *    system 앞에 도구가 오므로 표시 하나로 도구까지 같이 캐시된다.
     *    5분이 아니라 1시간인 이유: 종목을 연달아 분석할 때 두 번째 종목부터 이 구간을 그냥 읽는다.
     *
     * 2. 최상위에 표시 (5분)
     *    검색 결과가 쌓이는 뒷부분을 자동으로 잡아준다. 실제 돈이 나가는 건 여기다.
     *    이어서 호출할 때 지금까지 검색한 내용 전체가 제값 대신 10분의 1로 들어온다.
     *    턴 사이 간격이 몇 초라서 5분으로 충분하고, 쓰기 값이 1.25배라 1시간(2배) 보다 싸다.
     *    긴 캐시가 짧은 캐시보다 앞에 와야 하는데 system 이 messages 앞이라 순서도 맞는다.
     *
     * 사고·노력 설정을 호출마다 바꾸면 뒷부분 캐시가 통째로 날아간다. 그래서 둘 다 고정해 둔다.
     */
    private MessageCreateParams.Builder baseParams(String systemPrompt, String userPrompt) {
        return MessageCreateParams.builder()
                .model(properties.model())
                .maxTokens(properties.maxTokens())
                // 적응형 사고. budget_tokens 는 이 모델에서 제거되어 쓰면 400 이 난다.
                .thinking(ThinkingConfigAdaptive.builder().build())
                // effort 는 최상위가 아니라 OutputConfig 안에 중첩된다.
                // Effort 는 자바 enum 이 아니라서 valueOf 가 없다. of(문자열) 로 만든다.
                .outputConfig(OutputConfig.builder()
                        .effort(OutputConfig.Effort.of(properties.effort()))
                        .build())
                // 자동 캐싱. 대화가 길어지는 만큼 뒤로 따라가며 잡는다
                .cacheControl(CacheControlEphemeral.builder().build())
                .systemOfTextBlockParams(List.of(TextBlockParam.builder()
                        .text(systemPrompt)
                        .cacheControl(CacheControlEphemeral.builder()
                                .ttl(CacheControlEphemeral.Ttl.TTL_1H)
                                .build())
                        .build()))
                // 웹검색은 Anthropic 서버에서 돌아간다. 코드실행 도구는 같이 선언하지 않는다.
                .addTool(WebSearchTool20260209.builder()
                        .maxUses(properties.webSearchMaxUses().longValue())
                        .build())
                .addTool(submitTool())
                .addUserMessage(userPrompt);
    }

    private ToolUnion submitTool() {
        if (submitTool == null) {
            synchronized (this) {
                if (submitTool == null) {
                    submitTool = provider.strictTool(SUBMIT_TOOL,
                            "완성된 기업분석 결과를 제출한다. 반드시 정확히 한 번만 호출한다.",
                            "prompts/company-analysis-schema.json");
                }
            }
        }
        return submitTool;
    }

    /** 응답에서 submit_analysis 도구 호출을 찾아 JSON 문자열로 꺼낸다. 없으면 null */
    private String findSubmittedAnalysis(Message message) {
        for (ContentBlock block : message.content()) {
            if (block.isToolUse() && SUBMIT_TOOL.equals(block.asToolUse().name())) {
                try {
                    return objectMapper.writeValueAsString(block.asToolUse()._input());
                } catch (Exception e) {
                    throw new AppException("분석 결과를 JSON 으로 바꾸지 못했습니다: " + e.getMessage(), e);
                }
            }
        }
        return null;
    }

    /** 웹검색을 몇 번 했는지 센다 */
    private int countWebSearches(Message message) {
        int count = 0;
        for (ContentBlock block : message.content()) {
            if (block.isServerToolUse()) {
                count++;
            }
            // 서버 도구 에러는 예외를 던지지 않고 결과 블록 안에 담겨 온다.
            // 검색이 실패해도 남은 근거로 답을 만드는 경우가 많으므로 로그만 남기고 계속 간다.
            if (block.isWebSearchToolResult()) {
                log.debug("웹검색 결과 블록 수신");
            }
        }
        return count;
    }

    /** 턴이 중간에 끊겼는지 (pause_turn) */
    private boolean isPaused(Message message) {
        return message.stopReason()
                .map(reason -> reason.toString().toLowerCase().contains("pause"))
                .orElse(false);
    }
}
