package com.mystock.portfolio;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * .env 파일을 파싱해서 System Property 로 등록하는 초경량 로더.
 * 외부 라이브러리(spring-dotenv 등) 없이, 동작 원리가 눈에 보이도록 직접 구현했다.
 *
 * 등록된 System Property 는 application.yml 의 ${TOSS_CLIENT_ID} 같은 플레이스홀더가 그대로 읽어간다.
 */
public final class EnvFileLoader {

    private EnvFileLoader() {
    }

    public static void loadIntoSystemProperties(String path) {
        Path envPath = Path.of(path);
        if (!Files.exists(envPath)) {
            // .env 가 없어도 앱은 뜨도록 둔다 (배포 환경에서는 진짜 OS 환경변수를 쓸 수 있으므로).
            // 다만 토스 API 를 실제로 호출하는 시점에 TossAuthService 가 명확한 에러를 던진다.
            return;
        }
        try {
            List<String> lines = Files.readAllLines(envPath);
            for (String rawLine : lines) {
                String line = rawLine.strip();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = line.substring(0, eq).strip();
                String value = line.substring(eq + 1).strip();
                value = stripQuotes(value);

                // 이미 설정된 시스템 프로퍼티/OS 환경변수가 있으면 덮어쓰지 않는다 (운영 환경 값 우선).
                if (System.getProperty(key) == null && System.getenv(key) == null) {
                    System.setProperty(key, value);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(".env 파일을 읽는 중 오류가 발생했습니다.", e);
        }
    }

    private static String stripQuotes(String value) {
        boolean wrappedInDoubleQuotes = value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"");
        boolean wrappedInSingleQuotes = value.length() >= 2 && value.startsWith("'") && value.endsWith("'");
        if (wrappedInDoubleQuotes || wrappedInSingleQuotes) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
