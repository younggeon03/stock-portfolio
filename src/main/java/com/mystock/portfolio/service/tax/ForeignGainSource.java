package com.mystock.portfolio.service.tax;

import com.mystock.portfolio.brokerage.Broker;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 증권사 한 곳의 해외주식 실현손익(원화).
 *
 * ★ 왜 BrokerageClient 에 메서드를 더하지 않고 따로 뒀나
 * BrokerageClient 는 "지금 무엇을 들고 있나" 만 안다. 실현손익은 지난 매매 기록이 필요하고,
 * 증권사마다 주는 방식이 전혀 다르다(나무는 손익을 계산해서 주고, 토스는 체결 내역만 준다).
 * 직접 입력(MANUAL)처럼 기록이 없는 곳은 구현할 것도 없다.
 *
 * ★ 왜 증권사를 합쳐야 하나
 * 해외주식 양도소득 기본공제 250만 원은 사람 한 명 기준이다. 증권사가 둘이면 두 곳 손익을 합쳐야
 * 공제가 얼마 남았는지, 세금이 얼마인지 나온다. 증권사 앱은 자기 계좌만 보니 이 계산을 못 한다.
 */
public interface ForeignGainSource {

    Broker broker();

    /** .env 에 키가 있는지. 없으면 연결 안 한 것이지 실패가 아니다 */
    boolean isConfigured();

    /** 결제일이 [from, to] 안인 매도의 실현손익. 실패하면 예외를 던진다(한 곳 실패가 다른 곳을 막지 않게 서비스가 잡는다) */
    BrokerGains realized(LocalDate from, LocalDate to);

    /**
     * @param realizedKrw   실현손익 합계(원). 손실이면 음수
     * @param sellAmountKrw 매도 금액 합계(원)
     * @param sales         매도 한 건(또는 하루) 단위 내역. 증권사가 날짜별 합계만 주면 symbol 이 null
     * @param notes         계산에서 뺀 것·가정한 것. 숫자만 보고 믿지 않게 같이 보여준다
     */
    record BrokerGains(BigDecimal realizedKrw, BigDecimal sellAmountKrw, List<Sale> sales, List<String> notes) {
    }

    record Sale(String symbol, LocalDate date, BigDecimal quantity, BigDecimal sellAmountKrw, BigDecimal realizedKrw) {
    }
}
