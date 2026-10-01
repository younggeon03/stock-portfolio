package com.mystock.portfolio.service.importing;

import java.util.List;

/**
 * 스크린샷 분석 결과 전체. 화면의 검수 모달이 이 모양을 그린다.
 */
public record ImportPreview(

        /** 읽어낸 종목들 */
        List<ImportedHolding> holdings,

        /** 클로드가 남긴 메모. 읽기 어려웠던 부분 등 */
        String note,

        /** 사람이 확인해야 하는 줄이 몇 개인지 */
        int needsReviewCount,

        /**
         * 종목 마스터가 비어 있는지.
         * 비어 있으면 이름을 코드로 바꿀 수 없어서 매칭이 전부 실패한다.
         * 화면에서 "종목 목록을 먼저 받아오세요" 라고 안내해야 한다.
         */
        boolean symbolMasterEmpty,

        /** 앱이 붙이는 안내 문구 */
        String guide
) {

    public static final String GUIDE =
            "스크린샷에서 읽은 값입니다. 노란색으로 표시된 줄은 확인이 필요합니다. "
                    + "직접 고치거나 행을 지운 뒤 저장하세요.";
}
