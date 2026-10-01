package com.mystock.portfolio.service.importing;

import java.math.BigDecimal;
import java.util.List;

/**
 * 스크린샷에서 읽어 다듬은 한 줄. 화면의 검수 표에 그대로 뿌린다.
 *
 * ★ 바로 저장하지 않는다
 * 글자를 잘못 읽을 수 있으므로 사람이 확인하고 고친 뒤에 저장한다.
 * 그래서 "이 값이 얼마나 믿을 만한지" 와 "무엇이 문제인지" 를 같이 담아 보낸다.
 */
public record ImportedHolding(

        /** 화면에 적혀 있던 이름 그대로 (사용자가 무엇을 보고 매칭했는지 알 수 있게) */
        String rawName,

        /** 찾아낸 종목코드. 못 찾았으면 null */
        String symbol,

        /** 찾아낸 정식 종목명. 못 찾았으면 rawName 을 그대로 */
        String name,

        /** KR 또는 US. 못 정했으면 null */
        String marketCountry,

        /** KRW 또는 USD */
        String currency,

        /** 보유 수량. 못 읽었으면 null */
        BigDecimal quantity,

        /** 매수 평균가. 못 읽었으면 null */
        BigDecimal averagePurchasePrice,

        /** 종목 매칭이 얼마나 확실한지 0~100 */
        int symbolConfidence,

        /** 글자를 얼마나 확실하게 읽었는지 0~100 */
        int readConfidence,

        /**
         * 사람이 확인해야 하는지.
         * true 면 화면에서 노랗게 강조해 수정을 유도한다.
         */
        boolean needsReview,

        /**
         * 무엇이 문제인지 한국어로.
         * 예: ["종목을 찾지 못했습니다", "평단가를 읽지 못했습니다"]
         */
        List<String> issues
) {
}
