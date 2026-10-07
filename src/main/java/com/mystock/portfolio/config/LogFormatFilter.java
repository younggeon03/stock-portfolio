package com.mystock.portfolio.config;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

import java.util.Locale;

/**
 * logback-spring.xml 에서 줄글(text)·JSON 두 출력 중 LOG_FORMAT 에 맞는 하나만 통과시킨다.
 *
 * ★ 왜 이런 필터가 필요한가
 * 처음에는 appender-ref 이름을 변수로 바꿔 하나만 연결했다. 그러자 logback 이 "연결 안 된 appender 가 있다" 는
 * 경고를 내고, 경고가 있으면 내부 상태 메시지 수십 줄을 줄글로 쏟아냈다. 서버의 JSON 로그 맨 앞이 줄글로 더러워졌다.
 * 둘 다 연결하고 여기서 거르면 경고가 없다. 거르는 건 인코딩 전이라 버려지는 쪽은 비용이 거의 없다.
 *
 * 값은 대소문자를 가리지 않는다(LOG_FORMAT=JSON 으로 써도 로그가 사라지지 않게).
 * 둘 중 아무것도 아닌 값이면 줄글로 둔다. 로그가 통째로 안 나오는 것보다 낫다.
 */
public class LogFormatFilter extends Filter<ILoggingEvent> {

    private String expected = "text";
    private String actual = "text";

    /** 이 appender 가 맡는 모양. text 또는 json */
    public void setExpected(String expected) {
        this.expected = expected;
    }

    /** 설정된 LOG_FORMAT 값 */
    public void setActual(String actual) {
        this.actual = actual;
    }

    @Override
    public FilterReply decide(ILoggingEvent event) {
        return normalize(actual).equals(expected) ? FilterReply.NEUTRAL : FilterReply.DENY;
    }

    static String normalize(String value) {
        return value != null && "json".equals(value.trim().toLowerCase(Locale.ROOT)) ? "json" : "text";
    }
}
