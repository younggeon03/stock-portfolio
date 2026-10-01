package com.mystock.portfolio.external.anthropic;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolUnion;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mystock.portfolio.common.AppException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Claude 클라이언트를 만들고 공통 작업을 처리하는 곳.
 *
 * 기업분석과 스크린샷 읽기 두 군데서 Claude 를 쓰는데,
 * 클라이언트 만들기·에러 번역·도구 스키마 읽기가 똑같아서 여기로 모았다.
 *
 * ★★ fromEnv() 를 쓰지 않는 이유 (중요) ★★
 * 이 프로젝트의 EnvFileLoader 는 .env 값을 **System Property** 로 넣지 OS 환경변수로 넣지 않는다.
 * 그런데 SDK 의 fromEnv() 는 OS 환경변수와 로그인 프로파일을 본다. 그래서 두 가지 사고가 난다.
 *   1. .env 에 넣은 키를 못 찾는다
 *   2. 이 PC 에 Claude Code 로그인 프로파일이 있으면 **개인 계정으로 조용히 과금**된다
 * 그래서 반드시 apiKey 를 명시적으로 넘긴다.
 */
@Component
public class AnthropicClientProvider {

    private final AnthropicProperties properties;
    private final ObjectMapper objectMapper;

    /** 첫 호출 때 만든다. 키가 없어도 앱이 뜨게 하려고 지연 생성한다. */
    private volatile AnthropicClient client;

    public AnthropicClientProvider(AnthropicProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /** 키를 확인하고 클라이언트를 돌려준다 */
    public AnthropicClient client() {
        AnthropicClient existing = this.client;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (this.client != null) {
                return this.client;
            }
            if (!properties.hasApiKey()) {
                throw new AppException("""
                        Claude API 키가 설정되지 않았습니다.
                        프로젝트 루트의 .env 파일에 아래 한 줄을 채우고 앱을 재시작하세요.
                          ANTHROPIC_API_KEY=sk-ant-...
                        (https://console.anthropic.com 에서 발급하며, 크레딧이 충전되어 있어야 합니다)""");
            }
            this.client = AnthropicOkHttpClient.builder()
                    .apiKey(properties.apiKey())
                    .timeout(Duration.ofMinutes(properties.timeoutMinutes()))
                    .build();
            return this.client;
        }
    }

    /**
     * 결과를 받을 도구를 만든다.
     *
     * ★ 왜 도구로 받는가
     * 그냥 "JSON 으로 답해줘" 라고 하면 앞뒤에 설명을 붙이거나 형식이 틀어지기 쉽다.
     * strict=true 도구로 받으면 스키마에 맞는 값만 들어오므로 파싱이 안정적이다.
     *
     * 스키마는 자바 코드로 중첩해서 만들지 않고 리소스 JSON 파일에서 읽는다.
     * 사람이 읽고 고칠 수 있어야 하기 때문이다.
     */
    public ToolUnion strictTool(String name, String description, String schemaResourcePath) {
        Map<String, Object> schema = loadSchema(schemaResourcePath);

        @SuppressWarnings("unchecked")
        Map<String, Object> schemaProperties = (Map<String, Object>) schema.get("properties");
        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.get("required");

        Tool.InputSchema.Properties.Builder propertiesBuilder = Tool.InputSchema.Properties.builder();
        schemaProperties.forEach((key, value) -> propertiesBuilder.putAdditionalProperty(key, JsonValue.from(value)));

        Tool.InputSchema inputSchema = Tool.InputSchema.builder()
                .properties(propertiesBuilder.build())
                .putAdditionalProperty("required", JsonValue.from(required))
                .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                .build();

        return ToolUnion.ofTool(Tool.builder()
                .name(name)
                .description(description)
                .inputSchema(inputSchema)
                .strict(true)
                .build());
    }

    /** 리소스 폴더의 글자 파일을 읽는다 (프롬프트·스키마) */
    public String readResource(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AppException("파일을 읽지 못했습니다: " + path, e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadSchema(String path) {
        try {
            return objectMapper.readValue(readResource(path), Map.class);
        } catch (Exception e) {
            throw new AppException("스키마 파일을 읽지 못했습니다: " + path, e);
        }
    }

    /**
     * SDK 예외를 한국어 안내로 바꾼다.
     *
     * 영어 JSON 을 그대로 화면에 뿌리면 무슨 말인지 알기 어렵다.
     * 자주 겪는 상황은 "무엇을 하면 되는지" 까지 알려준다.
     */
    public String describeError(Exception e) {
        String raw = e.getMessage() == null ? "" : e.getMessage();
        String lower = raw.toLowerCase();

        if (lower.contains("credit balance is too low")) {
            return """
                    Anthropic 계정의 크레딧이 부족합니다.
                    https://console.anthropic.com 에 로그인해서 Plans & Billing 메뉴로 가
                    크레딧을 충전하거나 결제 수단을 등록하세요.
                    (API 키는 정상입니다. 잔액만 없는 상태입니다)""";
        }
        if (lower.contains("authentication") || lower.contains("invalid x-api-key") || lower.contains("401")) {
            return """
                    Claude API 키가 올바르지 않습니다.
                    .env 의 ANTHROPIC_API_KEY 를 다시 확인하고 앱을 재시작하세요.
                    키는 sk-ant- 로 시작합니다.""";
        }
        if (lower.contains("rate_limit") || lower.contains("429")) {
            return "Claude 요청 한도를 초과했습니다. 잠시 후 다시 시도하세요.";
        }
        if (lower.contains("image") && lower.contains("large")) {
            return "이미지가 너무 큽니다. 화면을 나눠 찍거나 크기를 줄여서 다시 올려주세요.";
        }
        if (lower.contains("overloaded")) {
            return "Claude 서버가 혼잡합니다. 잠시 후 다시 시도하세요.";
        }
        return "Claude 호출에 실패했습니다: " + raw;
    }
}
