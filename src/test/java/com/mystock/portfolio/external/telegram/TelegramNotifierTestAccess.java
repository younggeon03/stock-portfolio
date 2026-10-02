package com.mystock.portfolio.external.telegram;

/** 패키지 비공개 메서드를 다른 패키지 테스트에서 부르기 위한 다리. 실제로 텔레그램을 부르지 않는다 */
public final class TelegramNotifierTestAccess {

    private TelegramNotifierTestAccess() {
    }

    public static boolean enabled(String token, String chat) {
        return new TelegramNotifier(token, chat).enabled();
    }

    public static boolean sendWhileDisabled() {
        return new TelegramNotifier("", "").send("보내지면 안 되는 글");
    }

    public static String truncate(String text) {
        return TelegramNotifier.truncate(text);
    }
}
