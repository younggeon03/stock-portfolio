package com.mystock.portfolio.web;

import com.mystock.portfolio.service.institution.Overlap;

import java.util.List;

/** 컨트롤러의 입력 해석(패키지 비공개)을 다른 패키지의 테스트에서 부르기 위한 다리 */
public final class InstitutionControllerTestAccess {

    private InstitutionControllerTestAccess() {
    }

    public static List<Overlap.Mine> parse(String raw) {
        return InstitutionController.parseHoldings(raw);
    }
}
