package com.mystock.portfolio.web;

import com.mystock.portfolio.domain.Institution;
import com.mystock.portfolio.domain.InstitutionRepository;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 검색엔진용 robots.txt 와 sitemap.xml.
 *
 * 공개 화면만 긁어가게 하고, API·내 화면·로그인은 막는다. 사이트맵 주소가 바깥 주소(APP_BASE_URL)를 알아야 해서
 * 정적 파일이 아니라 여기서 만든다.
 */
@Hidden
@RestController
public class SeoController {

    private final InstitutionRepository institutions;
    private final String baseUrl;

    public SeoController(InstitutionRepository institutions,
                         @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this.institutions = institutions;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public String robots() {
        return """
                User-agent: *
                Allow: /$
                Allow: /public/
                Allow: /feeds/
                Disallow: /api/
                Disallow: /portfolio.html
                Disallow: /login
                Disallow: /swagger-ui
                Disallow: /v3/

                Sitemap: %s/sitemap.xml
                """.formatted(baseUrl);
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String sitemap() {
        List<String> urls = new java.util.ArrayList<>(List.of(
                baseUrl + "/", baseUrl + "/public/overlap.html", baseUrl + "/public/dividends.html",
                baseUrl + "/public/company.html"));
        for (Institution i : institutions.findByActiveTrueOrderBySortOrder()) {
            urls.add(baseUrl + "/public/institution.html?cik=" + i.getCik());
        }
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        for (String u : urls) {
            sb.append("  <url><loc>").append(AtomFeed.esc(u)).append("</loc></url>\n");
        }
        return sb.append("</urlset>\n").toString();
    }
}
