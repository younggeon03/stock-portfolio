package com.mystock.portfolio.external.anthropic;

import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mystock.portfolio.common.AppException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;

/**
 * 증권사 앱 스크린샷에서 보유 종목을 읽어낸다.
 *
 * ★ 이 기능이 필요한 이유
 * 증권사 API 로는 **키를 발급받은 본인의 계좌만** 조회된다.
 * 다른 사람 계좌를 동의받아 보는 기능이 아예 없다.
 * 그래서 남이 이 앱을 쓰려면 보유종목을 직접 알려주는 수밖에 없는데,
 * 종목·수량·평단가를 하나하나 타이핑하는 건 너무 번거롭다.
 *
 * 스크린샷 한 장이면 끝나게 만든다.
 *
 * ★ 읽은 결과를 그대로 저장하지 않는다
 * 글자를 잘못 읽을 수 있으므로 사람이 화면에서 확인하고 고친 뒤에 저장한다.
 * 그래서 confidence(확신도)를 같이 돌려준다. 낮은 줄은 화면에서 강조된다.
 */
@Component
public class ScreenshotParser {

    private static final Logger log = LoggerFactory.getLogger(ScreenshotParser.class);

    /** 결과를 제출받을 도구 이름 */
    private static final String SUBMIT_TOOL = "submit_holdings";

    /** 이미지 한 장의 최대 크기. 이보다 크면 거절한다 */
    public static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;

    private final AnthropicClientProvider provider;
    private final AnthropicProperties properties;
    private final ObjectMapper objectMapper;

    private final ClaudeMetrics metrics;

    public ScreenshotParser(AnthropicClientProvider provider,
                            AnthropicProperties properties,
                            ObjectMapper objectMapper,
                            ClaudeMetrics metrics) {
        this.provider = provider;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    /**
     * 스크린샷에서 보유 종목을 읽어낸다.
     *
     * @param imageBytes 이미지 원본
     * @param contentType "image/png", "image/jpeg" 등
     */
    public ScreenshotParseResult parse(byte[] imageBytes, String contentType) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new IllegalArgumentException("이미지가 비어 있습니다.");
        }
        if (imageBytes.length > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException(
                    "이미지가 너무 큽니다. 5MB 이하로 줄여서 올려주세요. (지금 "
                            + (imageBytes.length / 1024 / 1024) + "MB)");
        }

        long startedAt = System.currentTimeMillis();

        // 보내기 전에 줄인다. 실패하면 원본을 그대로 보낸다 — 값이 더 나갈 뿐 기능은 멀쩡하다
        Shrunk shrunk = shrink(imageBytes, contentType);
        String base64 = Base64.getEncoder().encodeToString(shrunk.bytes());

        MessageCreateParams params = MessageCreateParams.builder()
                .model(properties.model())
                .maxTokens(8000L)
                /*
                 * 지시문 + 제출 스키마를 캐시한다.
                 *
                 * 스크린샷은 보통 한 장으로 끝나지 않는다. 증권사 화면을 여러 장 찍어 연달아 올리게 되는데
                 * 그때마다 이 앞부분(합쳐 2천 토큰쯤)이 똑같이 다시 나간다.
                 * 이미지가 바뀌어도 도구·지시문 캐시는 살아있으므로 두 번째 장부터 10분의 1 값으로 들어온다.
                 *
                 * 5분짜리를 쓴다. 장을 연달아 올리는 작업이라 간격이 5분을 넘지 않고, 쓰기 값도 1시간의 절반이다.
                 */
                .systemOfTextBlockParams(List.of(TextBlockParam.builder()
                        .text(provider.readResource("prompts/screenshot-extract-system.md"))
                        .cacheControl(CacheControlEphemeral.builder().build())
                        .build()))
                .addTool(provider.strictTool(SUBMIT_TOOL,
                        "스크린샷에서 읽어낸 보유 종목 목록을 제출한다. 정확히 한 번만 호출한다.",
                        "prompts/screenshot-extract-schema.json"))
                // 이미지를 먼저 놓고 그 뒤에 지시문을 넣는 게 인식률이 좋다
                .addUserMessageOfBlockParams(List.of(
                        ContentBlockParam.ofImage(ImageBlockParam.builder()
                                .source(Base64ImageSource.builder()
                                        .mediaType(mediaType(shrunk.contentType()))
                                        .data(base64)
                                        .build())
                                .build()),
                        ContentBlockParam.ofText(TextBlockParam.builder()
                                .text("이 증권사 화면에서 보유 종목을 읽어 submit_holdings 도구로 제출해라.")
                                .build())))
                .build();

        Message message;
        try {
            message = metrics.record("screenshot", properties.model(), () -> provider.client().messages().create(params));
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException(provider.describeError(e), e);
        }

        String json = findSubmitted(message);
        if (json == null) {
            throw new AppException("스크린샷에서 종목을 읽어내지 못했습니다. "
                    + "보유종목 목록이 잘 보이는 화면을 다시 캡처해서 올려주세요.");
        }

        long elapsed = (System.currentTimeMillis() - startedAt) / 1000;
        log.info("스크린샷 분석 완료 - 입력 {}토큰(캐시쓰기 {} / 캐시읽기 {}) / 출력 {}토큰 / 소요 {}초",
                message.usage().inputTokens(),
                message.usage().cacheCreationInputTokens().orElse(0L),
                message.usage().cacheReadInputTokens().orElse(0L),
                message.usage().outputTokens(), elapsed);

        try {
            return objectMapper.readValue(json, ScreenshotParseResult.class);
        } catch (Exception e) {
            throw new AppException("읽어낸 결과를 해석하지 못했습니다: " + e.getMessage(), e);
        }
    }

    /** 줄인 결과. 못 줄였으면 원본이 그대로 담긴다 */
    record Shrunk(byte[] bytes, String contentType) {}

    /**
     * 보내기 전에 이미지를 줄인다.
     *
     * ★ 왜 줄이는가
     * 이미지 토큰은 `⌈가로/28⌉ × ⌈세로/28⌉` 이다. 변을 줄이면 값이 제곱으로 준다.
     * 휴대폰 캡처 1170×2532 는 그 자체로 3,822토큰이라 지시문·스키마(2,077)보다 크다.
     * 긴 변을 2000 으로 맞추면 2,376토큰이다.
     *
     * ★ 못 줄여도 그냥 간다
     * 여기서 예외를 던지면 값을 아끼려다 기능을 죽이는 꼴이 된다.
     * 줄이기는 값을 깎는 일이지 반드시 되어야 하는 일이 아니다.
     *
     * ★ PNG 로만 내보낸다
     * 증권사 화면은 글자가 전부다. JPEG 로 다시 압축하면 작은 숫자 획에 번짐이 생긴다.
     * 용량은 늘지만 잘못 읽는 것보다 낫고, 어차피 토큰 값은 용량이 아니라 크기로 매겨진다.
     */
    Shrunk shrink(byte[] original, String contentType) {
        int maxEdge = properties.screenshotMaxEdge() == null ? 2000 : properties.screenshotMaxEdge();
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(original));
            if (src == null) {
                return new Shrunk(original, contentType);   // 읽을 수 없는 형식
            }

            int longEdge = Math.max(src.getWidth(), src.getHeight());
            if (longEdge <= maxEdge) {
                log.debug("스크린샷 {}x{} - 줄일 필요 없음", src.getWidth(), src.getHeight());
                return new Shrunk(original, contentType);
            }

            double scale = (double) maxEdge / longEdge;
            int w = Math.max(1, (int) Math.round(src.getWidth() * scale));
            int h = Math.max(1, (int) Math.round(src.getHeight() * scale));

            // TYPE_INT_RGB 로 받는다. 투명 채널이 있으면 검은 바탕으로 깔려 글자가 묻힐 수 있다
            BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = dst.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            // 글자를 줄이는 거라 보간 품질을 가장 높게 둔다. 속도는 문제가 안 된다
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(src, 0, 0, w, h, null);
            g.dispose();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(dst, "png", out) || out.size() == 0) {
                return new Shrunk(original, contentType);
            }

            log.info("스크린샷 {}x{} -> {}x{} (이미지 토큰 {} -> {})",
                    src.getWidth(), src.getHeight(), w, h,
                    imageTokens(src.getWidth(), src.getHeight()), imageTokens(w, h));
            return new Shrunk(out.toByteArray(), "image/png");

        } catch (Exception e) {
            log.warn("스크린샷을 줄이지 못해 원본을 보냅니다 - {}", e.getMessage());
            return new Shrunk(original, contentType);
        }
    }

    /** 이 크기가 몇 토큰인지. 로그에서 절감을 눈으로 보려고 둔다 */
    static int imageTokens(int w, int h) {
        return (int) (Math.ceil(w / 28.0) * Math.ceil(h / 28.0));
    }

    /** 응답에서 도구 호출 결과를 꺼낸다 */
    private String findSubmitted(Message message) {
        for (ContentBlock block : message.content()) {
            if (block.isToolUse() && SUBMIT_TOOL.equals(block.asToolUse().name())) {
                try {
                    return objectMapper.writeValueAsString(block.asToolUse()._input());
                } catch (Exception e) {
                    throw new AppException("읽어낸 결과를 JSON 으로 바꾸지 못했습니다: " + e.getMessage(), e);
                }
            }
        }
        return null;
    }

    /** 업로드된 파일 형식을 SDK 가 아는 값으로 바꾼다 */
    private Base64ImageSource.MediaType mediaType(String contentType) {
        if (contentType == null) {
            return Base64ImageSource.MediaType.IMAGE_PNG;
        }
        return switch (contentType.toLowerCase()) {
            case "image/jpeg", "image/jpg" -> Base64ImageSource.MediaType.IMAGE_JPEG;
            case "image/gif" -> Base64ImageSource.MediaType.IMAGE_GIF;
            case "image/webp" -> Base64ImageSource.MediaType.IMAGE_WEBP;
            default -> Base64ImageSource.MediaType.IMAGE_PNG;
        };
    }
}
