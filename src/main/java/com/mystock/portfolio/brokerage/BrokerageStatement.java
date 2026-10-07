package com.mystock.portfolio.brokerage;

import java.math.BigDecimal;
import java.util.List;

/**
 * 증권사 한 곳의 잔고 응답: 보유 종목 + 증권사가 직접 밝힌 계좌 합계.
 *
 * ★ 왜 합계를 따로 들고 오나 (대사)
 * 화면의 숫자는 앱이 종목별 금액을 더한 것이다. 증권사도 같은 응답 맨 위에 "총 평가금액" 을 준다.
 * 둘이 다르면 앱이 무언가를 빠뜨렸거나(수량 0 으로 거른 줄, 모르는 통화) 증권사 응답 모양이 바뀐 것이다.
 * 실무의 원장 대사처럼 매일 장 마감 뒤 이 둘을 맞춰 보고, 어긋나면 기록하고 알린다(PortfolioSnapshotService).
 * 같은 응답에서 꺼내므로 API 를 더 부르지 않는다.
 *
 * @param holdings 표준 모델로 바꾼 보유 종목
 * @param totals   증권사가 밝힌 합계와, 같은 범위를 앱이 더한 값. 합계를 안 주는 곳(직접 입력)은 빈 목록
 */
public record BrokerageStatement(List<BrokerageHolding> holdings, List<ReportedTotal> totals) {

    public static BrokerageStatement withoutTotals(List<BrokerageHolding> holdings) {
        return new BrokerageStatement(holdings, List.of());
    }

    /**
     * 합계 한 줄. 증권사가 통화나 시장별로 나눠 주면 그 단위대로 여러 줄이다.
     *
     * @param scope       무엇의 합계인가. 예: "국내", "해외(원화)", "KRW", "USD"
     * @param currency    금액 단위. KRW 또는 USD
     * @param brokerTotal 증권사가 응답 요약에 적은 평가금액
     * @param appSum      앱이 같은 범위의 종목별 평가금액을 더한 값
     */
    public record ReportedTotal(String scope, String currency, BigDecimal brokerTotal, BigDecimal appSum) {
    }
}
