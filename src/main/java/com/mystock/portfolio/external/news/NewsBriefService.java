package com.mystock.portfolio.external.news;

import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.mystock.portfolio.external.anthropic.AnthropicClientProvider;
import com.mystock.portfolio.external.anthropic.AnthropicProperties;
import com.mystock.portfolio.external.anthropic.ClaudeMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 뉴스 헤드라인을 읽어 "지금 무슨 일이 있나" 를 한 덩어리로 정리한다.
 *
 * ★ 왜 필요한가
 * 헤드라인 열 줄을 그대로 늘어놓으면 결국 사람이 다 읽어야 한다.
 * 그중 무엇이 중요하고 주가에 어떤 방향으로 작용하는지가 알고 싶은 것이다.
 *
 * 뉴스를 다시 요약하는 게 아니라 **여러 헤드라인을 꿰어 하나의 그림**을 만든다.
 * 제목을 다시 나열하는 건 값어치가 없다.
 *
 * ★ 비용
 * 기업분석과 마찬가지로 **버튼을 눌러야 실행된다.** 뉴스 탭을 여는 것만으로 돈이 나가면 안 된다.
 * 그 위에 값을 더 낮춘다.
 *   - 헤드라인 제목만 보낸다. 본문을 읽지 않고 웹검색도 하지 않는다
 *   - 종목마다 하루 한 번만 만든다. 같은 날 다시 눌러도 만들어둔 걸 준다
 *   - 실패하면 조용히 넘어간다. 요약이 없어도 뉴스 목록은 그대로 보인다
 *   - 분석용 모델이 아니라 하쿠(anthropic.news-model)로 부른다. 제목 열 줄을 묶는 일이라 충분하다
 */
@Service
public class NewsBriefService {

    private static final Logger log = LoggerFactory.getLogger(NewsBriefService.class);

    /** 요약에 넣을 헤드라인 수. 늘려도 정확도가 크게 안 오르고 값만 오른다 */
    private static final int HEADLINE_LIMIT = 12;

    private final AnthropicClientProvider provider;
    private final AnthropicProperties properties;

    /**
     * 종목+날짜로 하루 한 번만 부르게 막는 캐시.
     *
     * 메모리에만 둔다. 재시작하면 그날 한 번 더 부르게 되지만, 한 번 값이 몇 원 수준이라
     * DB 테이블을 하나 더 만들 값어치가 없다. 기업분석은 건당 $0.5~2 라 DB 에 저장한다.
     */
    private final Map<String, Brief> cache = new ConcurrentHashMap<>();

    private final ClaudeMetrics metrics;

    public NewsBriefService(AnthropicClientProvider provider, AnthropicProperties properties, ClaudeMetrics metrics) {
        this.provider = provider;
        this.properties = properties;
        this.metrics = metrics;
    }

    /**
     * 오늘 이미 만들어둔 요약. 없으면 null.
     *
     * ★ 여기서는 절대 새로 만들지 않는다.
     * 뉴스 탭을 여는 것만으로 돈이 나가면 안 된다. 만드는 건 create() 가 한다.
     */
    public Brief cached(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        return cache.get(symbol.trim() + "@" + LocalDate.now());
    }

    /** ★ 여기서 돈이 나간다. 버튼을 눌렀을 때만 불린다 */
    public Brief create(String symbol, String name, List<NewsItem> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }

        String key = symbol + "@" + LocalDate.now();
        Brief already = cache.get(key);
        if (already != null) {
            return already;   // 같은 날 두 번 눌러도 한 번만 부른다
        }

        String headlines = items.stream()
                .limit(HEADLINE_LIMIT)
                .map(i -> "- " + i.title() + (i.pubDate() == null ? "" : " (" + i.pubDate() + ")"))
                .collect(Collectors.joining("\n"));

        try {
            String model = properties.newsModelOrDefault();
            MessageCreateParams.Builder builder = MessageCreateParams.builder()
                    .model(model)
                    /*
                     * ★ 출력 한도를 넉넉히 잡는다.
                     *
                     * 900 으로 두면 답이 잘릴 수 있었다. 분석용 모델은 사고가 기본으로 켜져 있어
                     * 사고가 한도를 다 먹으면 정작 답이 안 나온다. 잘린 호출도 요금은 그대로 나간다.
                     */
                    .maxTokens(1500L);
            /*
             * 분석용 모델로 돌릴 때만 노력을 낮춘다.
             * 헤드라인 10개를 세 줄로 줄이는 일에 긴 사고가 필요 없고, 출력은 입력의 5배 값이다.
             * 하쿠는 노력 설정을 받지 않아 넣으면 400 이 난다. 사고도 기본으로 꺼져 있어 낮출 게 없다.
             */
            if (supportsEffort(model)) {
                builder.outputConfig(OutputConfig.builder()
                        .effort(OutputConfig.Effort.of("low"))
                        .build());
            }
            MessageCreateParams params = builder
                    .system("""
                            너는 한국어로 답하는 증권 애널리스트다.
                            헤드라인 목록을 받아 "지금 이 종목에 무슨 일이 있고 그게 무슨 뜻인지" 를 정리한다.

                            ## 무엇을 쓰는가

                            뉴스 요약이 아니라 **인사이트**를 쓴다. 제목을 다시 나열하는 건 아무 값어치가 없다.
                            여러 헤드라인을 꿰어서 하나의 그림을 만들어라. 관련 없는 소식이 섞여 있으면
                            가장 중요한 줄기 하나만 잡고 나머지는 버려라.

                            ## 형식

                            아래 세 줄 그대로, 다른 말은 붙이지 말고 답해라.
                            `내용` 과 `영향` 은 각각 한 문단이다. 둘을 합쳐 두 문단을 넘기지 마라.

                            결론: (한 줄. 45자 이내. 판단이 담긴 문장이어야 한다)
                            내용: (무슨 일이 있었고 왜 중요한지. 180자 내외. 결론을 뒷받침하는 사실을 먼저 대고 끝에서 묶어라)
                            영향: (주가에 어떤 의미인지. 120자 내외. 방향과 그 이유를 같이 써라)

                            ## 지킬 것

                            - **헤드라인에 없는 사실을 지어내지 마라.** 제목만 보고 아는 것까지만 쓴다.
                              숫자를 본 적이 없으면 숫자를 쓰지 마라.
                            - **모호하게 끝내지 마라.** "지켜볼 필요가 있다", "혼조세를 보이고 있다" 같은
                              아무 말도 안 하는 문장을 쓰지 마라. 근거가 모자라면 모자라다고 분명히 써라.
                            - 매수·매도를 말하지 마라. 목표주가도 말하지 마라.
                            - 광고성 제목("급등주 추천", "무료 리딩", "지금 사야 할")은 없는 것으로 쳐라.
                            - 헤드라인이 전부 시세 중계뿐이라 잡을 줄기가 없으면
                              결론에 "이번 주 특별한 이슈는 없습니다" 라고 쓰고 그대로 끝내라.
                            """)
                    .addUserMessage("종목: " + name + " (" + symbol + ")\n\n헤드라인:\n" + headlines)
                    .build();

            Message message = metrics.record("news", model, () -> provider.client().messages().create(params));

            // 한도에 걸려 끊겼으면 알아야 한다. 조용히 넘어가면 잘린 글이 화면에 나가고,
            // 왜 이상한지 모른 채로 같은 값을 계속 내게 된다.
            boolean truncated = message.stopReason()
                    .map(r -> r.toString().toLowerCase().contains("max_tokens"))
                    .orElse(false);
            if (truncated) {
                log.warn("뉴스 요약이 출력 한도에 걸려 끊겼습니다 [{}]. maxTokens 를 올려야 합니다", symbol);
            }

            String text = message.content().stream()
                    .map(block -> block.text().map(t -> t.text()).orElse(""))
                    .collect(Collectors.joining("\n"))
                    .trim();

            Brief brief = parse(text);
            if (brief == null) {
                return null;
            }

            log.info("뉴스 요약 완료 [{}] {} - 입력 {}토큰 / 출력 {}토큰",
                    symbol, model, message.usage().inputTokens(), message.usage().outputTokens());
            cache.put(key, brief);
            return brief;

        } catch (Exception e) {
            // 요약이 없어도 뉴스 목록은 보여야 한다. 여기서 예외를 던지면 뉴스 탭이 통째로 죽는다
            log.warn("뉴스 요약 실패 [{}] - {}", symbol, e.getMessage());
            return null;
        }
    }

    /** 노력 설정을 받는 모델인지. 하쿠 4.5 는 안 받는다 */
    static boolean supportsEffort(String model) {
        return model == null || !model.contains("haiku");
    }

    /** "핵심:/내용:/영향:" 세 줄을 뽑는다. 모양이 틀리면 null */
    private Brief parse(String text) {
        String headline = null, detail = null, impact = null;
        String current = null;
        StringBuilder buffer = new StringBuilder();

        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            String next = null;
            if (trimmed.startsWith("결론:")) next = "headline";
            else if (trimmed.startsWith("내용:")) next = "detail";
            else if (trimmed.startsWith("영향:")) next = "impact";

            if (next != null) {
                if (current != null) {
                    String value = buffer.toString().trim();
                    if ("headline".equals(current)) headline = value;
                    else if ("detail".equals(current)) detail = value;
                    else impact = value;
                }
                current = next;
                buffer = new StringBuilder(trimmed.substring(trimmed.indexOf(':') + 1));
            } else if (current != null && !trimmed.isEmpty()) {
                buffer.append(' ').append(trimmed);
            }
        }
        if (current != null) {
            String value = buffer.toString().trim();
            if ("headline".equals(current)) headline = value;
            else if ("detail".equals(current)) detail = value;
            else impact = value;
        }

        if (headline == null || headline.isBlank()) {
            return null;
        }
        return new Brief(headline, detail == null ? "" : detail, impact == null ? "" : impact);
    }

    /** 뉴스 요약 한 덩어리 */
    public record Brief(String headline, String detail, String impact) {}
}
