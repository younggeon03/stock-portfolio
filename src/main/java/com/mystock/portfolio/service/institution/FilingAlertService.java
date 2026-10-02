package com.mystock.portfolio.service.institution;

import com.mystock.portfolio.external.telegram.TelegramNotifier;
import com.mystock.portfolio.external.thirteenf.NewFilingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 새 13F 를 받으면 알린다 (지금은 텔레그램. 피드는 FeedController 가 DB 에서 바로 만든다).
 *
 * 공개된 지 3일 넘은 제출은 알리지 않는다. 처음 배치를 돌리면 8분기치를 한꺼번에 받는데,
 * 그걸 전부 "새로 나왔다" 고 보내면 채널이 옛 소식으로 도배된다.
 */
@Service
public class FilingAlertService {

    private static final Logger log = LoggerFactory.getLogger(FilingAlertService.class);
    static final int MAX_AGE_DAYS = 3;

    private final InstitutionPortfolioService portfolio;
    private final TelegramNotifier telegram;
    private final String baseUrl;

    public FilingAlertService(InstitutionPortfolioService portfolio, TelegramNotifier telegram,
                              @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this.portfolio = portfolio;
        this.telegram = telegram;
        this.baseUrl = baseUrl;
    }

    @EventListener
    public void onNewFiling(NewFilingEvent e) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        if (!telegram.enabled() || !FilingAlerts.worthAlerting(e.filedDate(), e.holdingCount(), today, MAX_AGE_DAYS)) {
            return;
        }
        try {
            String text = FilingAlerts.body(e.institutionName(), portfolio.changes(e.cik(), e.reportPeriod()).orElse(null),
                    e.reportPeriod(), e.filedDate(), e.cik(), baseUrl);
            if (telegram.send(text)) {
                log.info("13F 알림 보냄: {} {}", e.institutionName(), e.reportPeriod());
            }
        } catch (Exception ex) {
            log.warn("13F 알림 만들기 실패 ({}): {}", e.institutionName(), ex.getMessage());
        }
    }
}
