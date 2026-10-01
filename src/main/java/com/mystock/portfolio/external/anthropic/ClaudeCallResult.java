package com.mystock.portfolio.external.anthropic;

/**
 * 클로드 호출 한 번의 결과.
 *
 * 분석 JSON 말고도 토큰 수를 같이 담는다.
 * 이 앱에서 유일하게 돈이 나가는 지점이라, 얼마나 썼는지 기록으로 남겨야 하기 때문이다.
 */
public record ClaudeCallResult(

        /** submit_analysis 도구로 제출받은 분석 JSON 원본 문자열 */
        String analysisJson,

        /** 사용한 모델 이름 */
        String model,

        /** 제값을 낸 입력 토큰. 캐시를 켜면 이 값은 "캐시에 없던 나머지" 만 남는다 */
        int inputTokens,

        /** 캐시에 새로 쓴 토큰. 1시간 캐시는 입력값의 2배를 받는다 */
        int cacheWriteTokens,

        /** 캐시에서 읽어온 토큰. 입력값의 10분의 1이다. 이게 커야 돈을 아낀 것이다 */
        int cacheReadTokens,

        /** 출력 토큰 수 */
        int outputTokens,

        /** 웹검색을 몇 번 했는지 */
        int webSearchCount,

        /** 소요 시간(초) */
        long elapsedSeconds
) {

    /**
     * 프롬프트 전체 크기.
     *
     * ★ inputTokens 만 보면 안 된다.
     * 캐시를 켠 뒤로 inputTokens 는 "캐시에 없던 나머지" 라서, 실제로 클로드가 읽은 양보다 훨씬 작다.
     * 캐싱 전후 기록을 같은 눈금으로 비교하려면 셋을 더해야 한다.
     */
    public int totalPromptTokens() {
        return inputTokens + cacheWriteTokens + cacheReadTokens;
    }

    /**
     * 이 호출에 든 돈(달러) 어림값.
     *
     * Opus 5 기준 입력 $5 / 출력 $25 per MTok, 캐시 읽기는 0.1배, 1시간 캐시 쓰기는 2배.
     * 웹검색은 1,000회당 $10.
     * 청구서와 소수점까지 맞추려는 게 아니라, 캐싱이 실제로 듣고 있는지 로그에서 바로 보려는 값이다.
     */
    public double estimatedUsd() {
        return (inputTokens * 5.0
                + cacheWriteTokens * 10.0
                + cacheReadTokens * 0.5
                + outputTokens * 25.0) / 1_000_000.0
                + webSearchCount * 0.01;
    }
}
