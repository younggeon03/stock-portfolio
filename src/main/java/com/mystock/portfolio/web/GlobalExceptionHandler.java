package com.mystock.portfolio.web;

import com.mystock.portfolio.common.AppException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 컨트롤러에서 난 예외를 {"error": "사람이 읽을 한국어"} 로 바꾼다.
 *
 * ★ 예상 못 한 예외의 내용은 응답에 싣지 않는다
 * 공개 서버에서 "서버 오류: " + 예외 메시지 를 그대로 주면 클래스 이름·SQL·내부 주소가 새어 나갈 수 있다.
 * 응답에는 일반 문구만 주고, 자세한 건 로그에 남긴다. 바깥 API 실패(AppException)와 입력 오류는
 * 우리가 직접 쓴 문장이라 그대로 보여준다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AppException.class)
    public ResponseEntity<Map<String, String>> handleAppException(AppException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, String>> handleMissingParam(MissingServletRequestParameterException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "필요한 값이 빠졌습니다: " + e.getParameterName()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleNotReadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "요청 형식이 올바르지 않습니다."));
    }

    /**
     * 없는 주소. 브라우저(HTML 을 원함)면 안내 화면을, 그 밖에는 JSON 을 준다.
     * 이게 없으면 아래 catch-all 이 이 예외까지 잡아서 500 으로 둔갑시킨다.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<?> handleNotFound(NoResourceFoundException e, HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("text/html") && !request.getRequestURI().startsWith("/api/")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                    .body(notFoundPage());
        }
        // 형식을 못박는다. 브라우저가 HTML 만 원한다고 해도 API 주소는 JSON 으로 답한다
        // (안 못박으면 스프링이 형식을 못 맞춰 기본 오류 화면을 띄운다)
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("error", "존재하지 않는 경로입니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception e, HttpServletRequest request) {
        log.error("처리하지 못한 예외 [{} {}]", request.getMethod(), request.getRequestURI(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "서버 오류가 났습니다. 잠시 뒤 다시 시도해 주세요."));
    }

    private static String cachedNotFound;

    private static String notFoundPage() {
        if (cachedNotFound == null) {
            try {
                cachedNotFound = new String(new ClassPathResource("static/public/404.html").getInputStream()
                        .readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException ex) {
                cachedNotFound = "<!DOCTYPE html><title>404</title><p>찾는 페이지가 없습니다. <a href=\"/\">첫 화면</a></p>";
            }
        }
        return cachedNotFound;
    }
}
