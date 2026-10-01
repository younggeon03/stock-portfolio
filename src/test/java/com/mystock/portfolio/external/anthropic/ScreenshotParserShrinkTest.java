package com.mystock.portfolio.external.anthropic;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스크린샷 줄이기 테스트.
 *
 * ★ 왜 이걸 테스트하는가
 * 이미지 값은 크기로 매겨진다. 토큰이 `⌈가로/28⌉ × ⌈세로/28⌉` 이라
 * 변을 줄이면 값이 제곱으로 준다. 스크린샷 한 장 값의 대부분이 여기다.
 *
 * 동시에 여기서 잘못되면 **돈을 아끼려다 기능이 죽는다.** 줄이기가 실패해도
 * 원본이 그대로 나가야 하고, 비율이 틀어지면 클로드가 표를 잘못 읽는다.
 * 그 두 가지를 고정한다.
 */
class ScreenshotParserShrinkTest {

    /** 긴 변 2000 으로 설정한 파서. 클로드를 부르지 않는 메서드만 쓰므로 나머지는 null 로 둔다 */
    private ScreenshotParser parser(Integer maxEdge) {
        return new ScreenshotParser(null,
                new AnthropicProperties(null, "claude-opus-5", "HIGH", 16000L, 8, 10, 7, maxEdge, null),
                null);
    }

    @Test
    void 큰_캡처는_긴_변_기준으로_줄어든다() throws Exception {
        // 휴대폰 캡처 크기
        byte[] big = png(1170, 2532);

        ScreenshotParser.Shrunk shrunk = parser(2000).shrink(big, "image/png");
        BufferedImage out = read(shrunk.bytes());

        assertThat(Math.max(out.getWidth(), out.getHeight())).isEqualTo(2000);
        assertThat(shrunk.contentType()).isEqualTo("image/png");
    }

    @Test
    void 가로세로_비율이_유지된다() throws Exception {
        // 비율이 틀어지면 표의 열이 어긋나 보여 숫자를 잘못 읽는다
        byte[] big = png(1170, 2532);

        BufferedImage out = read(parser(2000).shrink(big, "image/png").bytes());

        double before = 1170.0 / 2532.0;
        double after = (double) out.getWidth() / out.getHeight();
        assertThat(after).isCloseTo(before, org.assertj.core.data.Offset.offset(0.005));
    }

    @Test
    void 줄이면_토큰이_실제로_준다() throws Exception {
        // 이게 이 기능의 존재 이유다. 안 줄면 할 이유가 없다
        int before = ScreenshotParser.imageTokens(1170, 2532);
        BufferedImage out = read(parser(2000).shrink(png(1170, 2532), "image/png").bytes());
        int after = ScreenshotParser.imageTokens(out.getWidth(), out.getHeight());

        assertThat(before).isEqualTo(3822);
        assertThat(after).isLessThan(before);
        assertThat(after).isEqualTo(2376);
    }

    @Test
    void 이미_작으면_원본을_그대로_보낸다() throws Exception {
        // 작은 이미지를 굳이 다시 만들면 화질만 잃는다
        byte[] small = png(800, 1200);

        ScreenshotParser.Shrunk shrunk = parser(2000).shrink(small, "image/png");

        assertThat(shrunk.bytes()).isSameAs(small);
        assertThat(shrunk.contentType()).isEqualTo("image/png");
    }

    @Test
    void 못_읽는_파일이면_원본을_그대로_보낸다() {
        // 여기서 예외를 던지면 값을 아끼려다 등록 기능이 죽는다.
        // 줄이기는 값을 깎는 일이지 반드시 되어야 하는 일이 아니다.
        byte[] junk = "이건 이미지가 아니다".getBytes();

        ScreenshotParser.Shrunk shrunk = parser(2000).shrink(junk, "image/png");

        assertThat(shrunk.bytes()).isSameAs(junk);
    }

    @Test
    void 설정이_비어있어도_기본값으로_줄인다() throws Exception {
        // 설정을 빠뜨렸다고 안 줄이면 조용히 값이 두 배가 된다
        BufferedImage out = read(parser(null).shrink(png(1170, 2532), "image/png").bytes());

        assertThat(Math.max(out.getWidth(), out.getHeight())).isEqualTo(2000);
    }

    @Test
    void 토큰_계산은_28픽셀_단위로_올림한다() {
        // 1픽셀만 넘어가도 한 칸이 더 붙는다. 경계를 고정해 둔다
        assertThat(ScreenshotParser.imageTokens(28, 28)).isEqualTo(1);
        assertThat(ScreenshotParser.imageTokens(29, 28)).isEqualTo(2);
        assertThat(ScreenshotParser.imageTokens(1170, 2532)).isEqualTo(42 * 91);
    }

    // ── 테스트용 이미지 만들기 ──────────────────────────────

    /** 흰 바탕에 검은 글자 몇 줄. 증권사 화면과 비슷한 성격(글자 위주)으로 만든다 */
    private byte[] png(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.BLACK);
        for (int y = 40; y < h; y += 60) {
            g.drawString("삼성전자  10주  71,200원", 20, y);
        }
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private BufferedImage read(byte[] bytes) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }
}
