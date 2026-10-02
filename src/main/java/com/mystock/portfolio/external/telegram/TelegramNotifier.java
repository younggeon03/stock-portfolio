package com.mystock.portfolio.external.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 텔레그램 채널로 글을 보낸다. 봇 토큰과 채널이 없으면 꺼져 있다.
 *
 * ★ 만드는 법 (2분)
 * 텔레그램에서 @BotFather → /newbot → 토큰을 TELEGRAM_BOT_TOKEN 에.
 * 공개 채널을 만들고 봇을 관리자로 넣은 뒤, 채널 주소(@채널이름)를 TELEGRAM_CHAT_ID 에.
 *
 * ★ 실패해도 멈추지 않는다
 * 알림은 덤이다. 텔레그램이 막혀도 13F 받기와 화면은 그대로여야 한다. 경고만 남긴다.
 * 토큰은 주소 안에 들어가므로 로그에 주소를 찍지 않는다.
 */
@Component
public class TelegramNotifier {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotifier.class);
    /** 텔레그램 한 메시지 글자 한도 */
    static final int MAX_LENGTH = 4096;

    private final String token;
    private final String chatId;
    private final RestClient restClient = RestClient.create("https://api.telegram.org");

    public TelegramNotifier(@Value("${telegram.bot-token:}") String token,
                            @Value("${telegram.chat-id:}") String chatId) {
        this.token = token == null ? "" : token.strip();
        this.chatId = chatId == null ? "" : chatId.strip();
    }

    public boolean enabled() {
        return !token.isEmpty() && !chatId.isEmpty();
    }

    /** 보냈으면 true */
    public boolean send(String text) {
        if (!enabled()) {
            return false;
        }
        try {
            restClient.post()
                    .uri("/bot{token}/sendMessage", token)
                    .body(Map.of("chat_id", chatId, "text", truncate(text), "disable_web_page_preview", true))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            // 예외 메시지에 요청 주소(토큰 포함)가 섞일 수 있어 종류만 남긴다
            log.warn("텔레그램 발송 실패: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    static String truncate(String text) {
        return text.length() <= MAX_LENGTH ? text : text.substring(0, MAX_LENGTH - 1) + "…";
    }
}
