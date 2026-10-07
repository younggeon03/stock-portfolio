package com.mystock.portfolio.service.institution;

import com.mystock.portfolio.common.LogContext;
import com.mystock.portfolio.external.thirteenf.ThirteenFDataChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 13F 조회 캐시를 미리 채운다(예열).
 *
 * ★ 왜 필요한가 (부하 테스트에서 남은 숙제)
 * 조회 캐시는 배치가 데이터를 바꾸면 통째로 비운다. 그러면 그 뒤 첫 방문자가 "같이 산 종목" 계산(1~4초)을
 * 혼자 기다린다. 배포로 앱이 다시 떠도 캐시가 비어 같은 일이 생긴다. 사람이 오기 전에 첫 화면이 부르는 조회를
 * 한 번씩 계산해 두면 첫 방문자도 캐시에서 받는다.
 *
 * 언제: 앱이 다 떴을 때, 13F 배치가 끝났을 때(ThirteenFDataChangedEvent). 배치 중간의 새 제출 이벤트마다
 * 하지 않는다 — 기관마다 한 번씩 비워져서 그때마다 채우면 헛일이다.
 *
 * 무엇을: 첫 화면(home.js)이 부르는 것과 기관 상세의 기본 화면. 캐시 키가 요청 모양과 같아야 맞으므로
 * 화면이 쓰는 인자(limit 5, 기본 30, period 없음)를 그대로 쓴다. 화면이 인자를 바꾸면 여기도 바꿔야 한다.
 *
 * 실패해도 아무 일 없다. 사람이 왔을 때 계산하면 되는 값이라 로그만 남긴다.
 * 별도 빈인 이유: 같은 클래스 안에서 부르면 @Transactional(readOnly) 프록시를 거치지 않는다.
 */
@Component
public class InstitutionCacheWarmer {

    private static final Logger log = LoggerFactory.getLogger(InstitutionCacheWarmer.class);

    /** home.js 의 "같이 산 종목" 요약 */
    static final int HOME_CONSENSUS_LIMIT = 5;
    /** 컨트롤러 기본값. 같이 산 종목 전체 목록 */
    static final int DEFAULT_CONSENSUS_LIMIT = 30;

    private final InstitutionPortfolioService service;

    /** 요청 스레드·배치 스레드를 붙잡지 않게 따로 돈다. 하나면 충분하다(겹치면 뒤 것이 앞 것을 기다린다) */
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "cache-warmer");
        t.setDaemon(true);
        return t;
    });

    public InstitutionCacheWarmer(InstitutionPortfolioService service) {
        this.service = service;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        worker.submit(LogContext.job("warm-cache", this::warm));
    }

    /**
     * 배치가 끝났다. 서비스의 캐시 비우기가 먼저 돌아야 하므로 순서를 뒤로 둔다
     * (비우기 전에 채우면 옛 값을 채운 꼴이 된다. 둘 다 같은 이벤트를 듣는다)
     */
    @EventListener(ThirteenFDataChangedEvent.class)
    @Order(100)
    public void onBatchDone() {
        worker.submit(LogContext.carry(this::warm));
    }

    /** 실제 예열. 테스트에서 바로 부를 수 있게 열어 둔다 */
    void warm() {
        long started = System.currentTimeMillis();
        int done = 0;
        try {
            service.consensus(null, HOME_CONSENSUS_LIMIT);
            service.consensus(null, DEFAULT_CONSENSUS_LIMIT);
            done += 2;
            for (InstitutionPortfolioService.InstitutionView inst : service.list()) {
                service.holdings(inst.cik(), null);
                service.changes(inst.cik(), null);
                done += 2;
            }
            log.info("13F 조회 캐시 예열 {}건, {}ms", done, System.currentTimeMillis() - started);
        } catch (Exception e) {
            // 실패해도 사람이 왔을 때 계산하면 된다
            log.warn("13F 조회 캐시 예열을 {}건에서 멈춤: {}", done, e.getMessage());
        }
    }
}
