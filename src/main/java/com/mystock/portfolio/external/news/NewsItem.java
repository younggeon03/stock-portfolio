package com.mystock.portfolio.external.news;

/** 뉴스 헤드라인 한 건 (title 은 보통 "제목 - 언론사" 형태로 내려온다) */
public record NewsItem(String title, String link, String pubDate) {
}
