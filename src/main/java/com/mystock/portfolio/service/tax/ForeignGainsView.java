package com.mystock.portfolio.service.tax;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 화면에 넘기는 해외주식 양도소득 요약. 금액은 전부 원.
 *
 * @param realizedKrw           연결한 증권사의 실현손익 합계(손실이면 음수)
 * @param remainingDeductionKrw 기본공제 중 아직 안 쓴 금액. 이만큼은 더 이익을 실현해도 세금이 없다
 * @param taxableKrw            과세표준(합계 - 기본공제, 음수면 0)
 * @param estimatedTaxKrw       과세표준 × 22%. 원 미만 버림
 * @param partial               연결한 증권사 중 조회에 실패한 곳이 있어 합계가 반쪽인지
 */
public record ForeignGainsView(
        int year,
        LocalDate from,
        LocalDate to,
        BigDecimal realizedKrw,
        BigDecimal basicDeductionKrw,
        BigDecimal remainingDeductionKrw,
        BigDecimal taxableKrw,
        BigDecimal taxRate,
        BigDecimal estimatedTaxKrw,
        List<BrokerLine> brokers,
        boolean partial
) {

    /** 실패한 증권사는 realizedKrw 가 null 이고 error 에 원인이 있다 */
    public record BrokerLine(String broker, String brokerName, BigDecimal realizedKrw, BigDecimal sellAmountKrw,
                             int saleCount, List<String> notes, String error) {
    }
}
