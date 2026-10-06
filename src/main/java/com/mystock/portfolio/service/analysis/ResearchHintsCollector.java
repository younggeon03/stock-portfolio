package com.mystock.portfolio.service.analysis;

import com.mystock.portfolio.external.news.NewsItem;
import com.mystock.portfolio.external.news.NewsService;
import com.mystock.portfolio.service.institution.HoldingDiff;
import com.mystock.portfolio.service.institution.InstitutionPortfolioService;
import com.mystock.portfolio.service.institution.InstitutionPortfolioService.StockMove;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 기업분석 프롬프트에 넣을 "이미 가진 자료" 를 모은다. 전부 0원이다.
 * - 뉴스 헤드라인: 구글 뉴스 RSS (뉴스 탭과 같은 출처)
 * - 큰 기관 보유: 13F (DB). 미국 종목만
 *
 * 실패하면 빈 목록을 돌려준다. 이 자료가 없으면 예전처럼 클로드가 검색할 뿐이고, 분석은 막히지 않는다.
 */
@Component
public class ResearchHintsCollector {

    private static final Logger log = LoggerFactory.getLogger(ResearchHintsCollector.class);

    /** 제목 열 줄이면 최근 흐름을 잡기에 충분하다. 더 넣어도 토큰만 는다 */
    static final int HEADLINES = 10;
    /** 기관은 비중 큰 곳부터 다섯 줄과 요약 한 줄 */
    static final int INSTITUTIONS = 5;

    private static final Map<HoldingDiff.Kind, String> KIND = Map.of(
            HoldingDiff.Kind.NEW, "새로 삼",
            HoldingDiff.Kind.ADDED, "늘림",
            HoldingDiff.Kind.REDUCED, "줄임",
            HoldingDiff.Kind.SOLD_OUT, "다 팖",
            HoldingDiff.Kind.UNCHANGED, "그대로",
            HoldingDiff.Kind.SPLIT, "분할 추정");

    private final NewsService newsService;
    private final InstitutionPortfolioService institutionService;

    public ResearchHintsCollector(NewsService newsService, InstitutionPortfolioService institutionService) {
        this.newsService = newsService;
        this.institutionService = institutionService;
    }

    public ResearchHints collect(CompanyAnalysisFacts f) {
        return new ResearchHints(headlines(f), "US".equals(f.marketCountry()) ? institutions(f.symbol()) : List.of());
    }

    private List<String> headlines(CompanyAnalysisFacts f) {
        try {
            return newsService.searchStockNews(f.name()).stream()
                    .limit(HEADLINES)
                    .map(ResearchHintsCollector::line)
                    .toList();
        } catch (Exception e) {
            log.warn("{} 뉴스 제목을 못 받아 분석 프롬프트에 넣지 않습니다: {}", f.symbol(), e.getMessage());
            return List.of();
        }
    }

    static String line(NewsItem n) {
        return n.pubDate() == null || n.pubDate().isBlank() ? n.title() : n.title() + " (" + n.pubDate() + ")";
    }

    private List<String> institutions(String ticker) {
        try {
            return institutionLines(institutionService.movesFor(ticker).institutions());
        } catch (Exception e) {
            log.warn("{} 기관 보유를 못 읽어 분석 프롬프트에 넣지 않습니다: {}", ticker, e.getMessage());
            return List.of();
        }
    }

    /** 요약 한 줄 + 비중 큰 기관 다섯 줄. 안 가진 기관은 요약 수에만 들어간다 */
    static List<String> institutionLines(List<StockMove> moves) {
        if (moves == null || moves.isEmpty()) {
            return List.of();
        }
        List<StockMove> held = moves.stream().filter(m -> m.shares() > 0).toList();
        List<String> lines = new ArrayList<>();
        lines.add("따라가는 큰 기관 " + moves.size() + "곳 중 " + held.size() + "곳이 들고 있다");
        held.stream().limit(INSTITUTIONS).forEach(m -> {
            StringBuilder sb = new StringBuilder(m.nameKo()).append(": 비중 ")
                    .append(m.weightPercent().setScale(2, RoundingMode.HALF_UP).toPlainString()).append('%');
            if (m.kind() == null) {
                sb.append(", 바로 앞 분기와 비교 불가");
            } else {
                sb.append(", 앞 분기 대비 ").append(KIND.getOrDefault(m.kind(), m.kind().name()));
                if (m.sharesChangePercent() != null
                        && (m.kind() == HoldingDiff.Kind.ADDED || m.kind() == HoldingDiff.Kind.REDUCED)) {
                    sb.append(' ').append(m.sharesChangePercent().signum() > 0 ? "+" : "")
                            .append(m.sharesChangePercent().setScale(1, RoundingMode.HALF_UP).toPlainString()).append('%');
                }
            }
            sb.append(" (").append(m.period()).append(" 분기말)");
            lines.add(sb.toString());
        });
        return lines;
    }
}
