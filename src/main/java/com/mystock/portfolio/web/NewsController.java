package com.mystock.portfolio.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.mystock.portfolio.external.news.NewsBriefService;
import com.mystock.portfolio.external.news.NewsItem;
import com.mystock.portfolio.external.news.NewsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 종목 관련 최신 뉴스 헤드라인.
 *
 * 구글 뉴스 RSS 를 쓰므로 API 키가 필요 없고 **비용도 들지 않는다.**
 * 그래서 기업분석(유료)과 달리 종목을 클릭하면 자동으로 불러온다.
 *
 * 기업분석이 "이 회사가 어떤 회사인가" 를 다룬다면,
 * 뉴스는 "요즘 무슨 일이 있었나" 를 보여준다. 둘을 같이 보면 맥락이 잡힌다.
 *
 * 예: /api/news?query=엔비디아
 */
@Tag(name = "종목 뉴스", description = "목록은 무료. 인사이트는 POST /brief 를 눌렀을 때만 만들고 하루 한 번만 부른다")
@RestController
@RequestMapping("/api/news")
public class NewsController {

    private final NewsService newsService;
    private final NewsBriefService briefService;

    public NewsController(NewsService newsService, NewsBriefService briefService) {
        this.newsService = newsService;
        this.briefService = briefService;
    }

    /**
     * 뉴스 목록과 그 요약.
     *
     * 요약은 실패해도 목록은 그대로 나간다. 요약이 없다고 뉴스 탭이 비면 안 된다.
     *
     * @param query  검색어. 보통 종목명을 넘긴다.
     *               종목코드(005930)로 검색하면 엉뚱한 결과가 나오므로 이름을 쓰는 게 낫다.
     * @param symbol 요약 캐시 키로만 쓴다. 안 넘기면 요약 없이 목록만 준다.
     */
    @GetMapping
    public NewsResponse news(@RequestParam String query,
                             @RequestParam(required = false) String symbol) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query 파라미터가 필요합니다. 예: /api/news?query=엔비디아");
        }

        // 종목명을 따옴표로 묶고 "주가" 를 붙여 증권 기사로 좁힌다 (NewsService 주석 참고)
        List<NewsItem> items = newsService.searchStockNews(query.trim());

        // 오늘 이미 만들어둔 게 있으면 같이 준다. 여기서 새로 만들지는 않는다
        return new NewsResponse(briefService.cached(symbol), items);
    }

    /**
     * ★ 여기서 돈이 나간다. 화면의 "인사이트 보기" 버튼이 부른다.
     *
     * 같은 날 두 번 눌러도 한 번만 부르고 만들어둔 걸 돌려준다.
     */
    @PostMapping("/brief")
    public NewsResponse brief(@RequestParam String query, @RequestParam String symbol) {
        if (query == null || query.isBlank() || symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("query 와 symbol 이 모두 필요합니다.");
        }

        List<NewsItem> items = newsService.searchStockNews(query.trim());
        return new NewsResponse(briefService.create(symbol.trim(), query.trim(), items), items);
    }

    /** 화면이 받는 모양. 요약은 없을 수 있다 */
    public record NewsResponse(NewsBriefService.Brief brief, List<NewsItem> items) {}
}
