package com.mystock.portfolio.service.tax;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 증권사를 합친 올해 해외주식 양도소득과 예상 세액.
 *
 * ★ 숫자의 기준 (2026년 세법)
 *   - 해외주식 양도손익은 사람 단위로 1년치를 합친다. 한 증권사 손실이 다른 증권사 이익을 깎는다
 *   - 기본공제 250만 원, 세율 22%(양도소득세 20% + 지방소득세 2%)
 *   - 참고용이다. 신고는 증권사가 주는 양도세 자료로 한다. 화면에 그렇게 적는다
 *
 * ★ 연결 안 한 증권사는 빠진 게 아니다
 * 키가 없는 증권사는 결과에 넣지 않는다. 한 곳만 쓰는 사람에게는 그 한 곳이 전부다.
 * 키가 있는데 조회가 실패한 곳만 partial 로 표시한다(그때 합계는 반쪽이다).
 */
@Service
public class ForeignGainsService {

    private static final Logger log = LoggerFactory.getLogger(ForeignGainsService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    static final BigDecimal BASIC_DEDUCTION = new BigDecimal("2500000");
    static final BigDecimal TAX_RATE = new BigDecimal("0.22");

    /** 토스는 전체 주문을 페이지로 다 받아야 해서 무겁다. 같은 해를 10분 안에 다시 물으면 앞의 값을 준다 */
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final List<ForeignGainSource> sources;
    private final Clock clock;
    private final Map<Integer, Cached> cache = new ConcurrentHashMap<>();

    // 생성자가 둘이라(테스트용 시계 주입) 스프링이 쓸 쪽을 찍어 둔다. 안 찍으면 기동이 멈춘다
    @Autowired
    public ForeignGainsService(List<ForeignGainSource> sources) {
        this(sources, Clock.system(KST));
    }

    ForeignGainsService(List<ForeignGainSource> sources, Clock clock) {
        this.sources = sources;
        this.clock = clock;
    }

    public ForeignGainsView year(int year, boolean refresh) {
        Cached hit = cache.get(year);
        if (!refresh && hit != null && hit.at.plus(CACHE_TTL).isAfter(clock.instant())) {
            return hit.view;
        }
        ForeignGainsView view = compute(year);
        cache.put(year, new Cached(clock.instant(), view));
        return view;
    }

    ForeignGainsView compute(int year) {
        LocalDate today = LocalDate.now(clock);
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate end = LocalDate.of(year, 12, 31);
        LocalDate to = end.isAfter(today) ? today : end;

        List<ForeignGainsView.BrokerLine> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        boolean partial = false;

        for (ForeignGainSource source : sources) {
            if (!source.isConfigured()) continue;
            try {
                ForeignGainSource.BrokerGains g = source.realized(from, to);
                total = total.add(g.realizedKrw());
                lines.add(new ForeignGainsView.BrokerLine(source.broker().name(), source.broker().displayName(),
                        g.realizedKrw(), g.sellAmountKrw(), g.sales().size(), g.notes(), null));
            } catch (Exception e) {
                // 금액은 로그에 남기지 않는다. 어느 증권사가 실패했는지만
                log.warn("{} 해외주식 실현손익 조회 실패: {}", source.broker(), e.getMessage());
                partial = true;
                lines.add(new ForeignGainsView.BrokerLine(source.broker().name(), source.broker().displayName(),
                        null, null, 0, List.of(), e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            }
        }

        BigDecimal taxable = total.subtract(BASIC_DEDUCTION).max(BigDecimal.ZERO);
        BigDecimal remaining = BASIC_DEDUCTION.subtract(total.max(BigDecimal.ZERO)).max(BigDecimal.ZERO);
        BigDecimal tax = taxable.multiply(TAX_RATE).setScale(0, RoundingMode.DOWN);

        return new ForeignGainsView(year, from, to, total, BASIC_DEDUCTION, remaining, taxable, TAX_RATE, tax,
                lines, partial);
    }

    private record Cached(Instant at, ForeignGainsView view) {
    }
}
