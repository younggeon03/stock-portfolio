package com.mystock.portfolio.web;

import com.mystock.portfolio.service.dividend.DividendService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 배당 캘린더 (공개). 국내 종목의 DART 배당결정 공시로 기준일·지급일·주당 금액을 보여준다.
 * 입력은 저장하지 않는다. 받아 둔 공시만 DB 에 남는다(공개 자료).
 */
@Tag(name = "배당 캘린더", description = "DART 배당결정 공시로 만든 배당 일정. 무료, 로그인 없음")
@RestController
public class DividendController {

    private final DividendService dividendService;

    public DividendController(DividendService dividendService) {
        this.dividendService = dividendService;
    }

    /**
     * q=005930:10,현대차 — 종목코드나 정확한 회사 이름, ":" 뒤는 보유 주식 수(있으면 세전 예상 금액을 계산).
     * 20종목까지.
     */
    @GetMapping("/api/public/dividends")
    public DividendService.Calendar dividends(@RequestParam("q") String raw) {
        return dividendService.calendar(parse(raw));
    }

    static List<DividendService.Query> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("종목을 넣어 주세요. 예: 005930:10,현대차");
        }
        String[] parts = raw.split("[,\\n]");
        if (parts.length > DividendService.MAX_STOCKS) {
            throw new IllegalArgumentException("종목은 " + DividendService.MAX_STOCKS + "개까지 넣을 수 있습니다");
        }
        List<DividendService.Query> out = new ArrayList<>();
        for (String part : parts) {
            String p = part.strip();
            if (p.isEmpty()) {
                continue;
            }
            String[] kv = p.split(":");
            String name = kv[0].strip();
            if (name.length() > 40) {
                throw new IllegalArgumentException("종목 이름이 너무 깁니다: " + name.substring(0, 20) + "…");
            }
            Long shares = null;
            if (kv.length > 1 && !kv[1].isBlank()) {
                try {
                    shares = Long.parseLong(kv[1].strip().replace(",", ""));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("주식 수가 숫자가 아닙니다: " + p);
                }
                if (shares <= 0 || shares > 1_000_000_000L) {
                    throw new IllegalArgumentException("주식 수가 범위를 벗어났습니다: " + p);
                }
            }
            out.add(new DividendService.Query(name, shares));
        }
        if (out.isEmpty()) {
            throw new IllegalArgumentException("종목을 넣어 주세요. 예: 005930:10,현대차");
        }
        return out;
    }
}
