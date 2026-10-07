package com.mystock.portfolio.common;

import org.slf4j.MDC;

import java.util.Map;
import java.util.UUID;

/**
 * 로그 문맥(MDC)을 다른 스레드로 넘긴다.
 *
 * ★ 왜 필요한가
 * MDC 는 스레드마다 따로라서, 요청이 AI 분석처럼 일을 백그라운드 스레드로 넘기면 거기서 남긴 로그에는
 * 요청 ID 가 빠진다. "분석 시작" 은 번호가 있는데 정작 "분석 실패" 줄에는 없어서 둘을 못 잇는다.
 * 일을 넘길 때 지금 문맥을 복사해 가서 그 스레드에 깔고, 끝나면 치운다.
 *
 * 배치처럼 요청 없이 시작하는 일은 {@link #job} 으로 "batch-13f-…" 같은 번호를 새로 붙인다.
 */
public final class LogContext {

    private LogContext() {
    }

    /** 지금 스레드의 로그 문맥을 들고 가서 실행하는 Runnable */
    public static Runnable carry(Runnable task) {
        Map<String, String> context = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            if (context == null) MDC.clear(); else MDC.setContextMap(context);
            try {
                task.run();
            } finally {
                if (previous == null) MDC.clear(); else MDC.setContextMap(previous);
            }
        };
    }

    /** 요청 없이 시작하는 일(배치). 이름을 앞에 붙인 새 번호를 달고 실행한다 */
    public static Runnable job(String name, Runnable task) {
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            MDC.put("requestId", name + "-" + UUID.randomUUID().toString().substring(0, 8));
            try {
                task.run();
            } finally {
                if (previous == null) MDC.clear(); else MDC.setContextMap(previous);
            }
        };
    }
}
