package com.mystock.portfolio.external.anthropic;

import java.util.List;

/**
 * 스크린샷에서 읽어낸 원본 결과.
 *
 * 필드 이름이 screenshot-extract-schema.json 의 키와 정확히 같아야 한다.
 *
 * ★ 아직 다듬어지지 않은 상태다
 * 여기 담긴 name 은 화면에 적힌 글자 그대로이고, 종목코드가 아니다.
 * 숫자도 문자열이다. 코드 매칭과 숫자 변환은 ScreenshotImportService 가 한다.
 */
public record ScreenshotParseResult(

        /** 읽어낸 종목들 */
        List<Row> holdings,

        /** 읽으면서 어려웠던 점. 사용자에게 보여준다 */
        String note
) {

    /** 스크린샷에서 읽은 한 줄 */
    public record Row(

            /** 종목명 또는 티커. 화면에 적힌 그대로 */
            String name,

            /** 수량. 숫자만 남긴 문자열. 못 읽었으면 빈 문자열 */
            String quantity,

            /** 매수 평균가. 숫자만 남긴 문자열 */
            String averagePrice,

            /** KRW / USD / UNKNOWN */
            String currency,

            /** 이 줄을 얼마나 확실하게 읽었는지 0~100 */
            int confidence
    ) {
    }
}
