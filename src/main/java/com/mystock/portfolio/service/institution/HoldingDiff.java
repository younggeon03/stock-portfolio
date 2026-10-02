package com.mystock.portfolio.service.institution;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 두 분기의 13F 보유를 비교한다. DB 를 모르는 순수 계산이라 테스트로 고정한다.
 *
 * ★ 비교 단위는 주식 수다. 금액이 아니다
 * 금액은 주가가 오르기만 해도 늘어난다. "더 샀나" 는 주식 수로만 알 수 있다.
 *
 * ★ 액면분할을 "매수" 로 읽지 않는다
 * 10:1 분할이면 아무것도 안 사도 주식 수가 10배다. 주식 수 비율이 흔한 분할 비율(2·3·4·5·10·20…배,
 * 또는 그 역수)에 딱 맞고 기관 안에서의 비중이 거의 그대로면 분할로 본다.
 * 비중까지 보는 이유: 정말로 정확히 두 배를 더 산 경우도 있는데, 그때는 비중이 크게 오른다.
 *
 * ★ 옵션(풋·콜)은 뺀다
 * 옵션은 방향이 반대일 수 있고 만기로 사라진다. "보유 종목 변화" 에 섞으면 헷갈린다.
 */
public final class HoldingDiff {

    private HoldingDiff() {
    }

    /** 변화 종류 */
    public enum Kind { NEW, ADDED, REDUCED, SOLD_OUT, UNCHANGED, SPLIT }

    /** 비교에 쓰는 보유 한 줄 (주식만) */
    public record Position(String cusip, String ticker, String name, long shares, long valueUsd,
                           BigDecimal weightPercent) {
    }

    /** 한 종목의 변화 */
    public record Change(Kind kind, String cusip, String ticker, String name,
                         long sharesBefore, long sharesNow, BigDecimal sharesChangePercent,
                         long valueNow, BigDecimal weightBefore, BigDecimal weightNow) {
    }

    /** 이 비율이면 분할일 수 있다. 실제 분할 사례에서 흔한 것만 */
    private static final int[] SPLIT_RATIOS = {2, 3, 4, 5, 6, 8, 10, 15, 20, 25, 30, 40, 50};
    /** 비율이 분할 비율에서 이만큼 안쪽이면 맞는 것으로 본다 (몇 주 사고판 정도는 허용) */
    private static final double RATIO_TOLERANCE = 0.01;
    /** 분할이면 비중이 크게 안 변한다. 비중 변화가 이 비율 안쪽이어야 분할로 본다 */
    private static final double WEIGHT_TOLERANCE = 0.35;

    public static List<Change> diff(List<Position> now, List<Position> before) {
        Map<String, Position> prev = new LinkedHashMap<>();
        before.forEach(p -> prev.merge(p.cusip(), p, HoldingDiff::sum));
        Map<String, Position> cur = new LinkedHashMap<>();
        now.forEach(p -> cur.merge(p.cusip(), p, HoldingDiff::sum));

        List<Change> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Position n : cur.values()) {
            seen.add(n.cusip());
            Position b = prev.get(n.cusip());
            if (b == null) {
                out.add(change(Kind.NEW, n, 0, null));
                continue;
            }
            out.add(change(kindOf(b, n), n, b.shares(), b.weightPercent()));
        }
        for (Position b : prev.values()) {
            if (!seen.contains(b.cusip())) {
                out.add(new Change(Kind.SOLD_OUT, b.cusip(), b.ticker(), b.name(), b.shares(), 0,
                        BigDecimal.valueOf(-100).setScale(2, RoundingMode.HALF_UP), 0, b.weightPercent(),
                        BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)));
            }
        }
        // 큰 변화부터: 지금 금액이 큰 것, 다 판 것은 예전 비중이 큰 것
        out.sort(Comparator.comparingDouble((Change c) -> -impact(c)));
        return out;
    }

    static Kind kindOf(Position before, Position now) {
        if (before.shares() == now.shares()) {
            return Kind.UNCHANGED;
        }
        if (looksLikeSplit(before, now)) {
            return Kind.SPLIT;
        }
        return now.shares() > before.shares() ? Kind.ADDED : Kind.REDUCED;
    }

    static boolean looksLikeSplit(Position before, Position now) {
        if (before.shares() <= 0 || now.shares() <= 0) {
            return false;
        }
        double ratio = (double) now.shares() / before.shares();
        double r = ratio >= 1 ? ratio : 1 / ratio;
        boolean ratioFits = false;
        for (int s : SPLIT_RATIOS) {
            if (Math.abs(r - s) / s <= RATIO_TOLERANCE) {
                ratioFits = true;
                break;
            }
        }
        if (!ratioFits) {
            return false;
        }
        double wb = before.weightPercent() == null ? 0 : before.weightPercent().doubleValue();
        double wn = now.weightPercent() == null ? 0 : now.weightPercent().doubleValue();
        if (wb <= 0) {
            return false;
        }
        return Math.abs(wn - wb) / wb <= WEIGHT_TOLERANCE;
    }

    private static Change change(Kind kind, Position n, long sharesBefore, BigDecimal weightBefore) {
        BigDecimal pct = sharesBefore == 0 ? null
                : BigDecimal.valueOf(n.shares() - sharesBefore).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(sharesBefore), 2, RoundingMode.HALF_UP);
        return new Change(kind, n.cusip(), n.ticker(), n.name(), sharesBefore, n.shares(), pct,
                n.valueUsd(), weightBefore, n.weightPercent());
    }

    private static double impact(Change c) {
        if (c.kind() == Kind.SOLD_OUT) {
            return c.weightBefore() == null ? 0 : c.weightBefore().doubleValue();
        }
        return c.weightNow() == null ? 0 : c.weightNow().doubleValue();
    }

    /** 같은 CUSIP 이 두 줄이면 (주식 클래스가 같은데 운용 구분만 다른 경우) 합친다 */
    private static Position sum(Position a, Position b) {
        return new Position(a.cusip(), a.ticker(), a.name(), a.shares() + b.shares(), a.valueUsd() + b.valueUsd(),
                a.weightPercent() == null ? b.weightPercent()
                        : b.weightPercent() == null ? a.weightPercent() : a.weightPercent().add(b.weightPercent()));
    }
}
