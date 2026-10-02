package com.mystock.portfolio.service.stock;

import com.mystock.portfolio.external.filing.CompanyFinancials;
import com.mystock.portfolio.external.filing.CompanyFinancials.Period;
import com.mystock.portfolio.external.filing.CompanyFinancials.Valuation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 공시 재무를 "읽을 점" 문장으로 바꾼다. 공개 종목 창의 기업분석 칸이다.
 *
 * ★ 숫자를 LLM 에게 맡기지 않는다(결정기록 003). 여기 문장은 전부 정해진 규칙으로 만든다.
 *   같은 숫자면 항상 같은 문장이 나오고, 규칙은 테스트로 고정된다. 0원이다.
 * ★ 판정(사라/팔아라)과 적정가는 쓰지 않는다. 공개 화면이라 유사투자자문 선을 넘지 않는다.
 *   "무엇이 바뀌었나" 와 "무엇을 확인해야 하나" 까지만 말한다.
 */
public final class StockNotes {

    /** 성장이 "빨라졌다/느려졌다" 고 말할 차이(%p). 이보다 작으면 같은 속도로 본다 */
    static final BigDecimal GROWTH_SHIFT = BigDecimal.valueOf(2);
    /** 영업이익 대비 순이익 비율이 이만큼(%p) 움직이면 영업 밖 손익을 확인하라고 한다 */
    static final BigDecimal EARNINGS_GAP_SHIFT = BigDecimal.valueOf(5);
    /** 결산일 뒤 주가가 이만큼(%) 넘게 움직이면 PER 이 결산일과 다른 그림이라고 알린다 */
    static final BigDecimal PRICE_MOVE = BigDecimal.valueOf(15);

    private StockNotes() {
    }

    /** 한 칸. kind 는 화면이 순서·묶음을 정하는 데 쓴다 */
    public record Note(String kind, String title, String text) {
    }

    /**
     * @param priceChangePercent 마지막 결산일 종가 대비 지금 주가 변화(%). 모르면 null
     */
    public static List<Note> of(CompanyFinancials f, BigDecimal priceChangePercent) {
        List<Note> notes = new ArrayList<>();
        List<Period> years = f.annual() == null ? List.of() : f.annual().stream().filter(Objects::nonNull).toList();
        if (years.size() >= 2) {
            growth(years).ifPresent(notes::add);
            margin(years).ifPresent(notes::add);
            earningsGap(years).ifPresent(notes::add);
            leverage(years).ifPresent(notes::add);
        }
        valuation(f).ifPresent(notes::add);
        priceMove(f, priceChangePercent).ifPresent(notes::add);
        return notes;
    }

    /** 한 줄 요약. 가장 최근 해의 매출 증가율과 영업이익률. 둘 다 없으면 null */
    public static String summary(CompanyFinancials f) {
        List<Period> years = f.annual() == null ? List.of() : f.annual();
        if (years.size() < 2) {
            return null;
        }
        Period last = years.get(years.size() - 1);
        BigDecimal g = change(years.get(years.size() - 2).revenue(), last.revenue());
        if (g == null || last.operatingMargin() == null) {
            return null;
        }
        return label(last) + " 매출 " + signed(g) + ", 영업이익률 " + pct(last.operatingMargin());
    }

    // ── 규칙들 ──

    static Optional<Note> growth(List<Period> years) {
        int n = years.size();
        BigDecimal g2 = change(years.get(n - 2).revenue(), years.get(n - 1).revenue());
        if (g2 == null) {
            return Optional.empty();
        }
        String text = label(years.get(n - 1)) + " 매출이 " + moved(g2) + ".";
        if (n >= 3) {
            BigDecimal g1 = change(years.get(n - 3).revenue(), years.get(n - 2).revenue());
            if (g1 != null) {
                text = label(years.get(n - 2)) + " " + signed(g1) + ", " + label(years.get(n - 1)) + " " + signed(g2)
                        + ". " + pace(g1, g2);
            }
        }
        return Optional.of(new Note("GROWTH", "매출", text));
    }

    static Optional<Note> margin(List<Period> years) {
        Period first = years.get(0);
        Period last = years.get(years.size() - 1);
        if (first.operatingMargin() == null || last.operatingMargin() == null) {
            return Optional.empty();
        }
        BigDecimal d = last.operatingMargin().subtract(first.operatingMargin());
        String trend = d.compareTo(BigDecimal.ONE) >= 0 ? "해마다 이익이 더 많이 남는 쪽으로 가고 있습니다."
                : d.compareTo(BigDecimal.ONE.negate()) <= 0 ? "남는 몫이 줄고 있습니다. 비용이 매출보다 빨리 늘었습니다."
                : "남는 몫이 거의 그대로입니다.";
        String path = years.stream().map(p -> p.operatingMargin() == null ? "-" : pct(p.operatingMargin()))
                .reduce((a, b) -> a + " → " + b).orElse("");
        return Optional.of(new Note("MARGIN", "영업이익률", path + ". " + trend));
    }

    /**
     * 순이익이 영업이익보다 유난히 빨리(느리게) 늘었나.
     * 영업 밖 손익(투자 평가익, 일회성 처분익, 세금 효과)이 섞이면 EPS·PER 이 실제 장사보다 좋아(나빠) 보인다.
     */
    static Optional<Note> earningsGap(List<Period> years) {
        int n = years.size();
        BigDecimal before = ratio(years.get(n - 2).netIncome(), years.get(n - 2).operatingIncome());
        BigDecimal now = ratio(years.get(n - 1).netIncome(), years.get(n - 1).operatingIncome());
        if (before == null || now == null) {
            return Optional.empty();
        }
        BigDecimal d = now.subtract(before);
        if (d.abs().compareTo(EARNINGS_GAP_SHIFT) < 0) {
            return Optional.empty();
        }
        String text = d.signum() > 0
                ? "순이익이 영업이익보다 빨리 늘었습니다(영업이익 대비 순이익 " + pct0(before) + " → " + pct0(now) + "). "
                  + "영업 밖 이익(투자 평가익 등)이나 세금 효과가 섞였을 수 있습니다. EPS 를 그대로 믿기 전에 공시의 영업외손익과 법인세를 확인하세요."
                : "순이익이 영업이익만큼 늘지 못했습니다(영업이익 대비 순이익 " + pct0(before) + " → " + pct0(now) + "). "
                  + "영업 밖 손실이나 세금이 늘었을 수 있습니다. 공시의 영업외손익과 법인세를 확인하세요.";
        return Optional.of(new Note("EARNINGS_QUALITY", "확인할 점", text));
    }

    static Optional<Note> leverage(List<Period> years) {
        Period first = years.get(0);
        Period last = years.get(years.size() - 1);
        if (first.debtRatio() == null || last.debtRatio() == null) {
            return Optional.empty();
        }
        BigDecimal d = last.debtRatio().subtract(first.debtRatio());
        String trend = d.compareTo(BigDecimal.valueOf(-5)) <= 0 ? "빚 부담이 가벼워졌습니다."
                : d.compareTo(BigDecimal.valueOf(5)) >= 0 ? "빚 부담이 무거워졌습니다."
                : "빚 부담은 비슷합니다.";
        return Optional.of(new Note("LEVERAGE", "부채비율",
                pct0(first.debtRatio()) + " → " + pct0(last.debtRatio()) + ". " + trend));
    }

    /** 지금 PER 을 지난 결산일들의 PER 과 나란히. "싸다/비싸다" 대신 범위 안팎만 말한다 */
    static Optional<Note> valuation(CompanyFinancials f) {
        if (f.per() == null) {
            return Optional.empty();
        }
        List<BigDecimal> past = f.history() == null ? List.of()
                : f.history().stream().map(Valuation::per).filter(Objects::nonNull).toList();
        StringBuilder text = new StringBuilder("지금 PER " + f.per().setScale(1, RoundingMode.HALF_UP));
        if (f.pbr() != null) {
            text.append(", PBR ").append(f.pbr().setScale(1, RoundingMode.HALF_UP));
        }
        text.append('.');
        if (!past.isEmpty()) {
            text.append(" 지난 결산일들의 PER 은 ")
                .append(past.stream().map(p -> p.setScale(1, RoundingMode.HALF_UP).toPlainString())
                        .reduce((a, b) -> a + " → " + b).orElse(""))
                .append('.');
            BigDecimal min = past.stream().min(BigDecimal::compareTo).orElseThrow();
            BigDecimal max = past.stream().max(BigDecimal::compareTo).orElseThrow();
            if (f.per().compareTo(max) > 0) {
                text.append(" 지난 결산일들보다 높은 값에 거래되고 있습니다.");
            } else if (f.per().compareTo(min) < 0) {
                text.append(" 지난 결산일들보다 낮은 값에 거래되고 있습니다.");
            } else {
                text.append(" 지난 범위 안입니다.");
            }
        }
        return Optional.of(new Note("VALUATION", "밸류에이션", text.toString()));
    }

    static Optional<Note> priceMove(CompanyFinancials f, BigDecimal changePercent) {
        if (changePercent == null || changePercent.abs().compareTo(PRICE_MOVE) < 0
                || f.history() == null || f.history().isEmpty()) {
            return Optional.empty();
        }
        Valuation last = f.history().get(f.history().size() - 1);
        String when = last.tradeDate() == null ? "마지막 결산일" : "마지막 결산일(" + last.tradeDate() + ")";
        return Optional.of(new Note("PRICE", "주가",
                when + " 뒤로 주가가 " + signed(changePercent) + " 움직였습니다. "
                + "결산일 PER 과 지금 PER 이 다른 그림인 이유입니다. 움직인 이유는 이 자료로는 알 수 없습니다."));
    }

    // ── 숫자 도우미 ──

    /** before → now 변화율(%). before 가 0 이하이거나 둘 중 하나가 없으면 null (적자에서의 증가율은 뜻이 없다) */
    static BigDecimal change(BigDecimal before, BigDecimal now) {
        if (before == null || now == null || before.signum() <= 0) {
            return null;
        }
        return now.subtract(before).multiply(BigDecimal.valueOf(100)).divide(before, 1, RoundingMode.HALF_UP);
    }

    static BigDecimal ratio(BigDecimal part, BigDecimal whole) {
        if (part == null || whole == null || whole.signum() <= 0) {
            return null;
        }
        return part.multiply(BigDecimal.valueOf(100)).divide(whole, 1, RoundingMode.HALF_UP);
    }

    private static String pace(BigDecimal g1, BigDecimal g2) {
        if (g2.signum() < 0) {
            return "매출이 줄었습니다.";
        }
        if (g2.subtract(g1).compareTo(GROWTH_SHIFT) >= 0) {
            return "성장이 빨라지고 있습니다.";
        }
        if (g1.subtract(g2).compareTo(GROWTH_SHIFT) >= 0) {
            return "성장이 느려지고 있습니다.";
        }
        return "비슷한 속도로 늘고 있습니다.";
    }

    private static String moved(BigDecimal g) {
        return g.signum() >= 0 ? signed(g) + " 늘었습니다" : signed(g) + " 줄었습니다";
    }

    /** "FY2026 (2026-06-30 결산)" → "FY2026" */
    private static String label(Period p) {
        String l = p.label() == null ? "" : p.label();
        int space = l.indexOf(' ');
        return space > 0 ? l.substring(0, space) : l;
    }

    static String signed(BigDecimal v) {
        BigDecimal r = v.setScale(1, RoundingMode.HALF_UP);
        return (r.signum() > 0 ? "+" : "") + r.toPlainString() + "%";
    }

    private static String pct(BigDecimal v) {
        return v.setScale(1, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private static String pct0(BigDecimal v) {
        return v.setScale(0, RoundingMode.HALF_UP).toPlainString() + "%";
    }
}
