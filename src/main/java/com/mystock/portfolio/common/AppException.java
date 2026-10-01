package com.mystock.portfolio.common;

/** 외부 데이터 소스(시세 API, 뉴스 API) 호출 실패 등 앱 전반에서 쓰는 공용 예외 */
public class AppException extends RuntimeException {

    public AppException(String message) {
        super(message);
    }

    public AppException(String message, Throwable cause) {
        super(message, cause);
    }
}
