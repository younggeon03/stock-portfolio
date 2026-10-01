package com.mystock.portfolio.service.importing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.util.List;

/**
 * 직접 입력한 보유종목 저장 요청.
 *
 * 화면의 검수 표에서 사용자가 확인·수정을 마친 뒤 보내는 값이다.
 * 스크린샷에서 읽은 값을 그대로 저장하지 않고 반드시 이 단계를 거친다.
 */
public record ManualHoldingRequest(

        @NotNull(message = "holdings 목록이 필요합니다.")
        @Valid
        List<Row> holdings
) {

    public record Row(

            @NotBlank(message = "종목코드가 비어 있습니다. 종목을 골라주세요.")
            String symbol,

            @NotBlank(message = "종목명이 비어 있습니다.")
            String name,

            @NotBlank(message = "시장 구분(KR/US)이 필요합니다.")
            @Pattern(regexp = "KR|US", message = "시장 구분은 KR 또는 US 여야 합니다.")
            String marketCountry,

            @NotBlank(message = "통화가 필요합니다.")
            @Pattern(regexp = "KRW|USD", message = "통화는 KRW 또는 USD 여야 합니다.")
            String currency,

            @NotNull(message = "수량이 필요합니다.")
            @DecimalMin(value = "0.000001", message = "수량은 0보다 커야 합니다.")
            BigDecimal quantity,

            @NotNull(message = "평단가가 필요합니다.")
            @DecimalMin(value = "0.0001", message = "평단가는 0보다 커야 합니다.")
            BigDecimal averagePurchasePrice
    ) {
    }
}
