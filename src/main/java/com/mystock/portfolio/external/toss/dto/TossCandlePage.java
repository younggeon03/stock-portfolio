package com.mystock.portfolio.external.toss.dto;

import java.util.List;

/**
 * GET /api/v1/candles 의 result.
 *
 * 한 번에 최대 200개까지만 주기 때문에, 더 과거 데이터가 필요하면
 * nextBefore 를 다음 요청의 before 파라미터에 넣어서 이어서 받아야 한다.
 * 차트·변동성은 한 페이지면 되고, 과거 PER 용 결산일 종가를 찾을 때만 넘긴다 (closesOnOrBefore)
 */
public record TossCandlePage(

        /** 봉 목록. 최신순(내림차순)으로 온다 */
        List<TossCandle> candles,

        /** 다음 페이지 커서. 마지막 페이지면 null */
        String nextBefore
) {
}
