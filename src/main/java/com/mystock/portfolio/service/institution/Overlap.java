package com.mystock.portfolio.service.institution;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 내 포트폴리오와 기관 포트폴리오가 얼마나 겹치나. DB 를 모르는 순수 계산.
 *
 * ★ 겹침 = Σ min(내 비중, 기관 비중)
 * 펀드끼리 비슷한 정도를 잴 때 쓰는 표준 방식이다. 두 포트폴리오가 똑같으면 100%, 하나도 안 겹치면 0%.
 * 내가 애플 30%, 기관이 애플 22% 면 22%p 만큼 겹친다. 한쪽만 많이 든 부분은 겹친 게 아니다.
 *
 * ★ 내 비중은 입력한 종목 합계를 100% 로 다시 맞춘다
 * 사람이 손으로 넣는 비중은 합이 100 이 안 되기 일쑤다. 그대로 쓰면 겹침이 작게 나온다.
 * 13F 는 미국 주식만 있으므로 화면에서 "미국 주식만 넣으라" 고 안내한다.
 */
public final class Overlap {

    private Overlap() {
    }

    /** 내 보유 한 줄. weight 는 아무 단위나 (비율만 쓴다) */
    public record Mine(String ticker, double weight) {
    }

    /** 기관 보유 한 줄 (주식만, 티커가 있는 것만) */
    public record Theirs(String ticker, String name, BigDecimal weightPercent) {
    }

    public record Shared(String ticker, String name, BigDecimal mine, BigDecimal theirs, BigDecimal overlap) {
    }

    public record Result(BigDecimal overlapPercent, List<Shared> shared, List<Mine> onlyMine,
                         List<Theirs> theirTopMissing) {
    }

    /** 티커 표기를 맞춘다. brk-b, BRK/B, brk.b → BRK.B */
    public static String normalize(String ticker) {
        if (ticker == null) {
            return null;
        }
        return ticker.strip().toUpperCase().replace('-', '.').replace('/', '.');
    }

    public static Result compare(List<Mine> mine, List<Theirs> theirs, int missingLimit) {
        Map<String, Double> my = new LinkedHashMap<>();
        for (Mine m : mine) {
            if (m.ticker() != null && m.weight() > 0) {
                my.merge(normalize(m.ticker()), m.weight(), Double::sum);
            }
        }
        double sum = my.values().stream().mapToDouble(Double::doubleValue).sum();
        if (sum <= 0) {
            return new Result(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), List.of(), List.of(), List.of());
        }
        my.replaceAll((k, v) -> v * 100.0 / sum);

        Map<String, Theirs> th = new LinkedHashMap<>();
        for (Theirs t : theirs) {
            if (t.ticker() == null) {
                continue;
            }
            // GOOGL·GOOG 처럼 다른 티커는 다른 종목으로 둔다. 같은 티커가 두 줄이면 합친다
            th.merge(normalize(t.ticker()), t, (a, b) -> new Theirs(a.ticker(), a.name(),
                    a.weightPercent().add(b.weightPercent())));
        }

        List<Shared> shared = new ArrayList<>();
        List<Mine> onlyMine = new ArrayList<>();
        double total = 0;
        for (Map.Entry<String, Double> e : my.entrySet()) {
            Theirs t = th.get(e.getKey());
            if (t == null) {
                onlyMine.add(new Mine(e.getKey(), round(e.getValue()).doubleValue()));
                continue;
            }
            double o = Math.min(e.getValue(), t.weightPercent().doubleValue());
            total += o;
            shared.add(new Shared(e.getKey(), t.name(), round(e.getValue()), t.weightPercent(), round(o)));
        }
        shared.sort(Comparator.comparing(Shared::overlap).reversed());

        Set<String> mineKeys = my.keySet();
        List<Theirs> missing = th.values().stream()
                .filter(t -> !mineKeys.contains(normalize(t.ticker())))
                .sorted(Comparator.comparing(Theirs::weightPercent).reversed())
                .limit(missingLimit)
                .collect(Collectors.toList());

        return new Result(round(Math.min(total, 100.0)), shared, onlyMine, missing);
    }

    private static BigDecimal round(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }
}
