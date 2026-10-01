package com.mystock.portfolio.external.news;

import com.mystock.portfolio.common.AppException;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * [뉴스] Google News RSS 검색 피드에서 종목명 관련 최신 헤드라인을 가져온다.
 * 별도 API 키가 필요 없는 공개 RSS 이며, 개인적/비상업적 조회 용도로만 사용한다.
 */
@Service
public class NewsService {

    private static final int MAX_ITEMS = 5;

    /**
     * 스팸 헤드라인을 걸러내는 표시어.
     *
     * ★ 왜 필요한가
     * 구글 뉴스 RSS 는 품질이 낮은 사이트도 그대로 긁어온다.
     * 실제로 "현대차" 를 검색했더니 "DB판매", "카지노" 같은 광고 글이 섞여 나왔다.
     * 종목 뉴스와 아무 상관이 없으므로 제목에 이런 말이 있으면 버린다.
     */
    private static final List<String> SPAM_WORDS = List.of(
            "DB판매", "디비판매", "DB팝니다", "디비팝니다", "DB팜", "디비팜",
            "카지노", "토토", "먹튀", "벼룩시장", "구인구직", "성인");

    private final RestClient restClient;

    public NewsService(RestClient publicDataRestClient) {
        this.restClient = publicDataRestClient;
    }

    /**
     * 종목 뉴스를 찾는다.
     *
     * ★ 종목명만으로 검색하면 엉뚱한 결과가 나온다.
     * "현대차" 로 검색했더니 삼성전자 기사가 1위로 나왔다.
     * 그래서 이름을 따옴표로 묶어 정확히 일치시키고, "주가" 를 붙여 증권 기사로 좁힌다.
     */
    public List<NewsItem> searchStockNews(String stockName) {
        return searchNews("\"" + stockName + "\" 주가");
    }

    public List<NewsItem> searchNews(String keyword) {
        String encoded = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
        String url = "https://news.google.com/rss/search?q=" + encoded + "&hl=ko&gl=KR&ceid=KR:ko";

        // ★★ 반드시 URI 객체로 넘겨야 한다 ★★
        //
        // .uri(문자열) 로 넘기면 스프링이 그 문자열을 "URI 템플릿" 으로 보고 **한 번 더 인코딩** 한다.
        // 이미 인코딩해둔 한글이 %EA... → %25EA... 로 망가져서 구글이 검색어를 못 알아듣고
        // 엉뚱한 기본 뉴스 목록을 돌려준다. (실제로 "현대차" 를 검색했는데 삼성 기사가 나왔다)
        //
        // URI 객체로 넘기면 있는 그대로 요청하므로 이 문제가 없다.
        String xml = restClient.get()
                .uri(URI.create(url))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new AppException("뉴스 조회 실패 (" + keyword + ", " + res.getStatusCode() + ")");
                })
                .body(String.class);

        if (xml == null || xml.isBlank()) {
            return List.of();
        }
        return parseRssItems(xml);
    }

    private List<NewsItem> parseRssItems(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // 외부에서 받아온 XML 을 파싱하므로 XXE(외부 엔티티 주입) 공격을 막기 위해 관련 기능을 모두 비활성화한다.
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

            NodeList itemNodes = doc.getElementsByTagName("item");
            List<NewsItem> items = new ArrayList<>();

            // 스팸을 걸러내다 보면 개수가 줄어드므로, 넉넉히 훑으면서 MAX_ITEMS 만큼만 채운다
            for (int i = 0; i < itemNodes.getLength() && items.size() < MAX_ITEMS; i++) {
                Element item = (Element) itemNodes.item(i);
                String title = textOf(item, "title");
                if (isSpam(title)) {
                    continue;
                }
                items.add(new NewsItem(title, textOf(item, "link"), textOf(item, "pubDate")));
            }
            return items;
        } catch (Exception e) {
            throw new AppException("뉴스 응답 파싱 실패", e);
        }
    }

    /** 제목에 스팸 표시어가 들어있는지 */
    private boolean isSpam(String title) {
        if (title == null || title.isBlank()) {
            return true;
        }
        String upper = title.toUpperCase();
        return SPAM_WORDS.stream().anyMatch(word -> upper.contains(word.toUpperCase()));
    }

    private String textOf(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return "";
        }
        return nodes.item(0).getTextContent();
    }
}
