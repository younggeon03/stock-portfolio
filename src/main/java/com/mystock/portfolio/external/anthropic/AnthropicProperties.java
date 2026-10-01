package com.mystock.portfolio.external.anthropic;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Claude(Anthropic) API 설정.
 *
 * 토스·나무와 달리 **키가 하나뿐이다.** client_id/secret 같은 쌍이 없다.
 * https://console.anthropic.com 에서 발급하며 `sk-ant-` 로 시작한다.
 * 사용한 만큼 과금되므로 콘솔에 크레딧이 충전돼 있어야 한다.
 */
@ConfigurationProperties(prefix = "anthropic")
public record AnthropicProperties(

        /** API 키. .env 의 ANTHROPIC_API_KEY */
        String apiKey,

        /** 사용할 모델. claude-opus-5 */
        String model,

        /** 생각의 깊이. LOW / MEDIUM / HIGH / XHIGH / MAX */
        String effort,

        /** 한 번 응답에 쓸 수 있는 최대 출력 토큰 */
        Long maxTokens,

        /** 한 번 분석에 허용할 웹검색 횟수 */
        Integer webSearchMaxUses,

        /** HTTP 타임아웃(분). 웹검색을 여러 번 하면 오래 걸린다 */
        Integer timeoutMinutes,

        /** 이 일수가 지나면 화면에서 "오래된 분석" 으로 표시 */
        Integer cacheDays,

        /**
         * 스크린샷을 보내기 전에 줄일 긴 변의 최대 길이(px).
         *
         * ★ 이미지 값은 넓이 × 높이로 붙는다.
         * 토큰 수가 `⌈가로/28⌉ × ⌈세로/28⌉` 이라 변을 반으로 줄이면 값은 4분의 1이 된다.
         * 휴대폰 캡처(1170×2532)는 그 자체로 3,822토큰이라 지시문·스키마(2,077)보다 크다.
         *
         * 너무 줄이면 숫자를 잘못 읽는다. 읽기에 지장이 없는 선에서 정해야 하고,
         * 그 선은 실제 캡처로 확인해야 안다. 그래서 코드가 아니라 설정으로 뺐다.
         */
        Integer screenshotMaxEdge,

        /**
         * 뉴스 인사이트에만 쓰는 모델. 비어 있으면 model 을 쓴다.
         *
         * 헤드라인 열 줄을 세 줄로 묶는 일에 분석용 모델은 과하다. 하쿠면 값이 10분의 1이다.
         * 분석·스크린샷은 숫자를 틀리면 안 되는 일이라 그대로 model 을 쓴다.
         */
        String newsModel
) {

    /** 키가 채워져 있는지 */
    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** 뉴스 인사이트에 실제로 쓸 모델 */
    public String newsModelOrDefault() {
        return newsModel == null || newsModel.isBlank() ? model : newsModel;
    }
}
