package com.mystock.portfolio.service.tax;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 체결 내역만 주는 증권사(토스)의 해외주식 실현손익을 원화로 계산한다.
 *
 * ★ 규칙 세 가지 (소득세법의 해외주식 양도소득 계산을 따른다)
 *   1. 먼저 산 것부터 판 것으로 본다(선입선출). 증권사 앱의 "평단가" 는 이동평균이라 값이 다를 수 있다
 *   2. 원화 환산은 거래할 때 환율이 아니라 **결제일의 기준환율(매매기준율)** 이다. 산 주식은 산 결제일, 판 주식은 판 결제일
 *   3. 수수료·세금(필요경비)은 빼고 계산한다. 산 쪽 수수료는 취득가에, 판 쪽은 양도가에서
 *
 * ★ 모르는 건 지어내지 않는다
 * 판 수량만큼의 매수 기록이 없으면(조회 기간 밖, API 로 안 보이는 주문 유형 등) 그 수량은 계산에서 빼고
 * notes 에 남긴다. 평단가로 메우면 숫자는 나오지만 틀린 세금이 된다.
 *
 * 외부를 부르지 않는 순수 계산이다. 환율은 함수로 받아서 테스트에서 고정값을 넣는다.
 */
public final class FifoGainCalculator {

    private static final int KRW_SCALE = 0;

    /** 체결된 주문 한 건. amount·commission·tax 는 거래 통화(달러) */
    public record Fill(String symbol, boolean buy, BigDecimal quantity, BigDecimal amount,
                       BigDecimal commission, BigDecimal tax, LocalDate settlementDate, String filledAt) {
    }

    private FifoGainCalculator() {
    }

    /**
     * @param fills      전체 기간의 체결(산 것 포함). 순서는 상관없다(체결 시각으로 정렬한다)
     * @param from       결제일 기준 시작(포함)
     * @param to         결제일 기준 끝(포함)
     * @param usdKrwRate 결제일 → 그날 기준환율
     */
    public static ForeignGainSource.BrokerGains calculate(List<Fill> fills, LocalDate from, LocalDate to,
                                                          Function<LocalDate, BigDecimal> usdKrwRate) {
        List<Fill> sorted = new ArrayList<>(fills);
        sorted.sort(Comparator.comparing(Fill::filledAt));

        Map<String, Deque<Lot>> lots = new HashMap<>();
        List<ForeignGainSource.Sale> sales = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        BigDecimal realized = BigDecimal.ZERO;
        BigDecimal sellAmount = BigDecimal.ZERO;

        for (Fill f : sorted) {
            Deque<Lot> queue = lots.computeIfAbsent(f.symbol(), k -> new ArrayDeque<>());
            if (f.buy()) {
                // 취득가 = 체결금액 + 산 쪽 수수료·세금. 원화로는 나중에, 팔 때 매칭되는 몫만 바꾼다
                queue.addLast(new Lot(f.quantity(), f.amount().add(nz(f.commission())).add(nz(f.tax())), f.settlementDate()));
                continue;
            }

            // 판 주식도 앞에서부터 매수 묶음을 소진한다. 기간 밖 매도도 소진은 해야 뒤의 매도가 맞는 묶음을 만난다
            boolean inPeriod = !f.settlementDate().isBefore(from) && !f.settlementDate().isAfter(to);
            BigDecimal remaining = f.quantity();
            BigDecimal costKrw = BigDecimal.ZERO;
            while (remaining.signum() > 0 && !queue.isEmpty()) {
                Lot lot = queue.peekFirst();
                BigDecimal take = remaining.min(lot.quantity);
                BigDecimal costUsd = lot.costUsd.multiply(take).divide(lot.quantity, 10, RoundingMode.HALF_UP);
                if (inPeriod) costKrw = costKrw.add(costUsd.multiply(usdKrwRate.apply(lot.settlementDate)));
                lot.costUsd = lot.costUsd.subtract(costUsd);
                lot.quantity = lot.quantity.subtract(take);
                remaining = remaining.subtract(take);
                if (lot.quantity.signum() == 0) queue.pollFirst();
            }
            if (!inPeriod) continue;

            // 매수 기록이 모자라면 매칭된 몫만큼의 매도만 계산에 넣는다
            BigDecimal matched = f.quantity().subtract(remaining);
            if (remaining.signum() > 0) {
                notes.add(f.symbol() + " " + f.settlementDate() + " 매도 " + remaining.stripTrailingZeros().toPlainString()
                        + "주는 매수 기록을 못 찾아 계산에서 뺐습니다");
            }
            if (matched.signum() == 0) continue;

            BigDecimal proceedsUsd = f.amount().subtract(nz(f.commission())).subtract(nz(f.tax()))
                    .multiply(matched).divide(f.quantity(), 10, RoundingMode.HALF_UP);
            BigDecimal proceedsKrw = proceedsUsd.multiply(usdKrwRate.apply(f.settlementDate()));
            BigDecimal gain = proceedsKrw.subtract(costKrw).setScale(KRW_SCALE, RoundingMode.HALF_UP);
            BigDecimal proceedsRounded = proceedsKrw.setScale(KRW_SCALE, RoundingMode.HALF_UP);

            sales.add(new ForeignGainSource.Sale(f.symbol(), f.settlementDate(), matched, proceedsRounded, gain));
            realized = realized.add(gain);
            sellAmount = sellAmount.add(proceedsRounded);
        }
        return new ForeignGainSource.BrokerGains(realized, sellAmount, sales, notes);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** 아직 덜 판 매수 묶음. 부분 매도가 나면 수량과 취득가가 같이 줄어든다 */
    private static final class Lot {
        BigDecimal quantity;
        BigDecimal costUsd;
        final LocalDate settlementDate;

        Lot(BigDecimal quantity, BigDecimal costUsd, LocalDate settlementDate) {
            this.quantity = quantity;
            this.costUsd = costUsd;
            this.settlementDate = settlementDate;
        }
    }
}
