package com.mystock.portfolio.external.anthropic;

import com.anthropic.models.messages.Message;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Claude 호출 지표. 호출 수·소요 시간·성공/실패(claude_requests_seconds)와 토큰 수(claude_tokens_total).
 *
 * ★ 왜 따로 만드나
 * 바깥 API 지표(http_client_requests_seconds)는 RestClient 에만 붙는다. 앤트로픽 SDK 는 자기 HTTP 클라이언트를 써서
 * 거기 안 잡힌다. 그런데 이 앱에서 돈이 나가는 건 Claude 뿐이라 가장 봐야 할 호출이다.
 * 토큰을 종류별(제값·캐시쓰기·캐시읽기·출력)로 세어 두면 Grafana 에서 하루·한 달 사용량과
 * "캐시 읽기가 0 인가(캐싱이 죽었나)" 를 바로 본다(CLAUDE.md 의 그 확인을 지표로).
 *
 * 태그: kind = analysis|reassess|screenshot|news, model, outcome = success|error. 종목은 태그로 달지 않는다(시계열이 불어남).
 */
@Component
public class ClaudeMetrics {

    private final MeterRegistry registry;

    public ClaudeMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** 테스트처럼 지표를 볼 일 없는 곳에서 쓴다 */
    public static ClaudeMetrics noop() {
        return new ClaudeMetrics(new SimpleMeterRegistry());
    }

    /** SDK 호출 한 번을 재고 토큰을 센다. 예외는 그대로 다시 던진다 */
    public Message record(String kind, String model, Supplier<Message> call) {
        long started = System.nanoTime();
        try {
            Message message = call.get();
            timer(kind, model, "success").record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
            countTokens(kind, model, message);
            return message;
        } catch (RuntimeException e) {
            timer(kind, model, "error").record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
            throw e;
        }
    }

    private Timer timer(String kind, String model, String outcome) {
        return Timer.builder("claude.requests")
                .description("Claude 호출 한 번의 소요 시간")
                .tag("kind", kind).tag("model", safe(model)).tag("outcome", outcome)
                .register(registry);
    }

    private void countTokens(String kind, String model, Message message) {
        if (message == null || message.usage() == null) {
            return;
        }
        var usage = message.usage();
        add(kind, model, "input", usage.inputTokens());
        add(kind, model, "cache_write", usage.cacheCreationInputTokens().orElse(0L));
        add(kind, model, "cache_read", usage.cacheReadInputTokens().orElse(0L));
        add(kind, model, "output", usage.outputTokens());
    }

    private void add(String kind, String model, String type, long tokens) {
        Counter.builder("claude.tokens")
                .description("Claude 토큰 수(종류별)")
                .tag("kind", kind).tag("model", safe(model)).tag("type", type)
                .register(registry)
                .increment(tokens);
    }

    private static String safe(String model) {
        return model == null || model.isBlank() ? "unknown" : model;
    }
}
