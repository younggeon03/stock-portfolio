package com.mystock.portfolio.web;

import com.mystock.portfolio.domain.Filing13F;
import com.mystock.portfolio.domain.Filing13FRepository;
import com.mystock.portfolio.domain.Institution;
import com.mystock.portfolio.domain.InstitutionRepository;
import com.mystock.portfolio.service.institution.FilingAlerts;
import com.mystock.portfolio.service.institution.InstitutionPortfolioService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 새 13F 피드 (Atom). 피드 리더(Feedly 등)에 넣으면 기관이 새 13F 를 낼 때마다 알 수 있다.
 * 텔레그램과 달리 토큰이 필요 없어 지금 바로 쓸 수 있는 알림 채널이다.
 */
@Tag(name = "피드", description = "새 13F 를 Atom 피드로. 무료, 로그인 없음")
@RestController
public class FeedController {

    private final Filing13FRepository filings;
    private final InstitutionRepository institutions;
    private final InstitutionPortfolioService portfolio;
    private final String baseUrl;

    public FeedController(Filing13FRepository filings, InstitutionRepository institutions,
                          InstitutionPortfolioService portfolio,
                          @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this.filings = filings;
        this.institutions = institutions;
        this.portfolio = portfolio;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @GetMapping(value = "/feeds/13f.xml", produces = "application/atom+xml;charset=UTF-8")
    public ResponseEntity<String> thirteenF() {
        Map<Long, Institution> byCik = institutions.findAll().stream()
                .collect(Collectors.toMap(Institution::getCik, Function.identity()));
        List<AtomFeed.Entry> entries = filings.findTop20ByHoldingCountGreaterThanOrderByFiledDateDescReportPeriodDesc(0)
                .stream()
                .filter(f -> byCik.containsKey(f.getCik()) && byCik.get(f.getCik()).isActive())
                .map(f -> entry(f, byCik.get(f.getCik())))
                .toList();
        String xml = AtomFeed.build("tag:stock-portfolio,2026:13f", "기관 포트폴리오 — 새 13F",
                baseUrl + "/feeds/13f.xml", baseUrl + "/", entries);
        // 하루 한 번 바뀌는 자료다. 피드 리더가 자주 와도 서버가 매번 만들지 않게 한 시간 캐시
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .contentType(MediaType.parseMediaType("application/atom+xml;charset=UTF-8")).body(xml);
    }

    private AtomFeed.Entry entry(Filing13F f, Institution inst) {
        String body = FilingAlerts.body(inst.getNameKo(), portfolio.changes(f.getCik(), f.getReportPeriod()).orElse(null),
                f.getReportPeriod(), f.getFiledDate(), f.getCik(), baseUrl);
        return new AtomFeed.Entry("tag:stock-portfolio,2026:13f:" + f.getAccessionNo(),
                FilingAlerts.title(inst.getNameKo(), f.getReportPeriod()),
                baseUrl + "/public/institution.html?cik=" + f.getCik() + "&period=" + f.getReportPeriod(),
                f.getFiledDate(), body);
    }
}
