package com.mystock.portfolio.service.analysis;

import com.mystock.portfolio.external.filing.CompanyFinancials;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * 공시 재무로 재무상태·수치 지표 섹션의 표를 앱이 직접 채운다.
 *
 * ★ 왜 모델에게 표를 안 맡기나 (토큰 절감)
 * 예전에는 [공시 재무] 표를 프롬프트로 넣고, 모델이 그 숫자를 지표 JSON 으로 다시 옮겨 적었다.
 * 앱이 이미 가진 숫자를 출력 토큰(입력의 5배 값)으로 되받는 셈이었고, 옮기다 틀릴 여지도 있었다.
 * 이제 모델은 그 표를 해석한 문장만 쓰고, 표는 받은 뒤 여기서 같은 모양(Metric·Point)으로 끼운다.
 * 화면(analysis-view.js)은 누가 만든 표인지 모른 채 똑같이 그린다.
 *
 * 웹에서만 아는 값(적정가·컨센서스 배수)은 여전히 모델이 쓰고, 이 표 뒤에 붙는다.
 */
final class FinancialMetrics {

    static final String FINANCIAL_POSITION = "FINANCIAL_POSITION";
    static final String VALUATION_METRICS = "VALUATION_METRICS";

    /** 연간은 최근 3년. 지시문의 "연간 최근 3개" 와 같다 */
    private static final int YEARS = 3;

    private FinancialMetrics() {
    }

    /**
     * 분석 결과에 앱 표를 끼운 사본. 재무가 없거나 섹션이 성립하지 않으면(ETF 등) 그대로 둔다.
     * 모델이 지시를 어기고 같은 이름의 지표를 또 썼으면 앱 것을 남긴다. 공시 원본이 더 정확하다.
     */
    static CompanyAnalysisView inject(CompanyAnalysisView view, CompanyFinancials f) {
        if (view == null || f == null || f.annual() == null || f.annual().isEmpty()) {
            return view;
        }
        List<CompanyAnalysisView.Section> sections = new ArrayList<>();
        for (CompanyAnalysisView.Section s : view.sections()) {
            if (!s.applicable()) {
                sections.add(s);
            } else if (FINANCIAL_POSITION.equals(s.key())) {
                sections.add(merge(s, f, positionMetrics(f)));
            } else if (VALUATION_METRICS.equals(s.key())) {
                sections.add(merge(s, f, valuationMetrics(f)));
            } else {
                sections.add(s);
            }
        }
        return new CompanyAnalysisView(view.symbol(), view.name(), view.instrumentType(),
                view.oneLineSummary(), view.verdict(), sections, view.risks());
    }

    /** 앱 표를 앞에, 모델이 쓴 나머지 지표를 뒤에. 출처는 공시 원문을 섹션 출처 목록에 더해 번호를 단다 */
    private static CompanyAnalysisView.Section merge(CompanyAnalysisView.Section s, CompanyFinancials f,
                                                     List<Draft> drafts) {
        if (drafts.isEmpty()) {
            return s;
        }
        List<CompanyAnalysisView.Source> sources = new ArrayList<>(s.sources());
        int sourceIndex = sourceIndex(sources, f);

        List<CompanyAnalysisView.Metric> metrics = new ArrayList<>();
        Set<String> appLabels = new java.util.HashSet<>();
        for (Draft d : drafts) {
            List<CompanyAnalysisView.Metric.Point> points = d.points().stream()
                    .map(p -> new CompanyAnalysisView.Metric.Point(p.period(), p.periodType(), p.value(), sourceIndex))
                    .toList();
            metrics.add(new CompanyAnalysisView.Metric(d.label(), d.note(), points));
            appLabels.add(d.label());
        }
        for (CompanyAnalysisView.Metric m : s.metrics()) {
            if (!appLabels.contains(m.label())) {
                metrics.add(m);
            }
        }
        return new CompanyAnalysisView.Section(s.key(), s.title(), s.applicable(), s.notApplicableReason(),
                s.body(), s.bullets(), metrics, sources);
    }

    /** 공시 원문을 출처 목록에 넣고 그 번호를 돌려준다. 모델이 이미 같은 주소를 넣었으면 그 번호를 쓴다 */
    private static int sourceIndex(List<CompanyAnalysisView.Source> sources, CompanyFinancials f) {
        if (f.sources() == null || f.sources().isEmpty()) {
            return -1;
        }
        int first = -1;
        for (CompanyFinancials.Source src : f.sources()) {
            int found = -1;
            for (int i = 0; i < sources.size(); i++) {
                if (src.url() != null && src.url().equals(sources.get(i).url())) {
                    found = i;
                    break;
                }
            }
            if (found < 0) {
                sources.add(new CompanyAnalysisView.Source(src.title(), src.url(), f.filer(), ""));
                found = sources.size() - 1;
            }
            if (first < 0) {
                first = found;
            }
        }
        return first;
    }

    // ── 표 만들기 ────────────────────────────────────────

    /** 재무상태. 연간은 "연도" 탭, 올해 누적·최근 4분기는 "분기" 탭에 들어간다 */
    static List<Draft> positionMetrics(CompanyFinancials f) {
        boolean usd = "USD".equals(f.currency());
        boolean owners = f.annual().stream().anyMatch(p -> p.ownersNetIncome() != null);
        List<Draft> out = new ArrayList<>();
        add(out, f, "매출", "", p -> money(p.revenue(), usd), true);
        add(out, f, "영업이익", "", p -> money(p.operatingIncome(), usd), true);
        add(out, f, "영업이익률", "영업이익 ÷ 매출", p -> pct(p.operatingMargin()), true);
        add(out, f, owners ? "지배순이익" : "순이익",
                owners ? "비지배지분을 뺀 지배기업 주주 몫" : "", p -> money(owners ? p.ownersNetIncome() : p.netIncome(), usd), true);
        add(out, f, "자산총계", "", p -> money(p.totalAssets(), usd), false);
        add(out, f, "부채총계", "", p -> money(p.totalLiabilities(), usd), false);
        add(out, f, "자본총계", "비지배지분 포함", p -> money(p.totalEquity(), usd), false);
        add(out, f, "부채비율", "부채 ÷ 자본총계", p -> pct(p.debtRatio()), false);
        return out;
    }

    /** 수치 지표. 결산일 종가로 잰 과거 배수(연도)와 지금 주가로 잰 배수(시점값) */
    static List<Draft> valuationMetrics(CompanyFinancials f) {
        boolean usd = "USD".equals(f.currency());
        List<Draft> out = new ArrayList<>();
        List<CompanyFinancials.Valuation> history = f.history() == null ? List.of() : f.history();

        List<Point> per = new ArrayList<>();
        List<Point> pbr = new ArrayList<>();
        for (CompanyFinancials.Valuation v : latestFirst(history)) {
            per.add(new Point(v.label(), "ANNUAL", v.per() == null ? "적자" : times(v.per())));
            pbr.add(new Point(v.label(), "ANNUAL", v.pbr() == null ? "-" : times(v.pbr())));
        }
        if (!per.isEmpty()) {
            out.add(new Draft("PER", "그 해 결산일 종가 ÷ 그 해 EPS", per));
            out.add(new Draft("PBR", "그 해 결산일 종가 ÷ 그 해 말 BPS", pbr));
        }
        if (f.per() != null || f.pbr() != null) {
            String basis = f.ttm() != null ? "현재가 ÷ 최근 4분기 EPS" : "현재가 ÷ 최근 연간 EPS";
            out.add(new Draft("PER (현재가)", basis,
                    List.of(new Point("현재가 기준", "POINT", f.per() == null ? "계산 불가" : times(f.per())))));
            out.add(new Draft("PBR (현재가)", "현재가 ÷ 최근 BPS",
                    List.of(new Point("현재가 기준", "POINT", f.pbr() == null ? "계산 불가" : times(f.pbr())))));
        }
        add(out, f, "ROE", "지배주주 순이익 ÷ 지배주주 자본", p -> pct(p.roe()), false, true);
        add(out, f, "EPS", "", p -> perShare(p.eps(), usd), false, true);
        add(out, f, "BPS", "", p -> perShare(p.bps(), usd), false, true);
        return out;
    }

    private static void add(List<Draft> out, CompanyFinancials f, String label, String note,
                            Function<CompanyFinancials.Period, String> value, boolean flow) {
        add(out, f, label, note, value, flow, false);
    }

    /**
     * 한 지표의 기간별 값을 모은다. 값이 없는 기간은 뺀다(빈 칸이 "모름" 으로 읽히지 않게).
     *
     * @param flow       손익처럼 기간 동안 쌓이는 값이면 true. 올해 누적과 최근 4분기를 둘 다 넣는다.
     *                   재무상태처럼 한 시점의 값이면 올해 누적(가장 최근 분기말)만 넣는다
     * @param annualOnly ROE·EPS 처럼 1년치일 때만 뜻이 있는 값. 누적 기간은 비워 둔 값이라 넣지 않는다
     */
    private static void add(List<Draft> out, CompanyFinancials f, String label, String note,
                            Function<CompanyFinancials.Period, String> value, boolean flow, boolean annualOnly) {
        List<Point> points = new ArrayList<>();
        if (!annualOnly) {
            if (f.interim() != null) {
                point(points, f.interim(), "QUARTER", value);
            }
            if (flow && f.ttm() != null) {
                point(points, f.ttm(), "QUARTER", value);
            }
        }
        for (CompanyFinancials.Period p : latestFirst(f.annual())) {
            point(points, p, "ANNUAL", value);
        }
        if (!points.isEmpty()) {
            out.add(new Draft(label, note, points));
        }
    }

    private static void point(List<Point> points, CompanyFinancials.Period p, String type,
                              Function<CompanyFinancials.Period, String> value) {
        String v = value.apply(p);
        if (v != null) {
            points.add(new Point(p.label(), type, v));
        }
    }

    /** 공시는 오래된 해부터 온다. 화면은 최신이 왼쪽이라 뒤집고 최근 3개만 */
    private static <T> List<T> latestFirst(List<T> oldestFirst) {
        List<T> out = new ArrayList<>(oldestFirst);
        java.util.Collections.reverse(out);
        return out.subList(0, Math.min(YEARS, out.size()));
    }

    // ── 숫자 모양 ────────────────────────────────────────

    /** 원은 조·억원, 달러는 B·M. 표에서 자릿수를 세지 않고 읽히게 */
    static String money(BigDecimal amount, boolean usd) {
        if (amount == null) {
            return null;
        }
        BigDecimal abs = amount.abs();
        if (usd) {
            BigDecimal billion = BigDecimal.valueOf(1_000_000_000L);
            return abs.compareTo(billion) >= 0
                    ? "$" + strip(amount.divide(billion, 1, RoundingMode.HALF_UP)) + "B"
                    : "$" + comma(amount.divide(BigDecimal.valueOf(1_000_000L), 0, RoundingMode.HALF_UP)) + "M";
        }
        BigDecimal jo = new BigDecimal("1000000000000");
        return abs.compareTo(jo) >= 0
                ? strip(amount.divide(jo, 1, RoundingMode.HALF_UP)) + "조원"
                : comma(amount.divide(BigDecimal.valueOf(100_000_000L), 0, RoundingMode.HALF_UP)) + "억원";
    }

    private static String perShare(BigDecimal value, boolean usd) {
        if (value == null) {
            return null;
        }
        return usd ? "$" + value.setScale(2, RoundingMode.HALF_UP).toPlainString() : comma(value) + "원";
    }

    private static String pct(BigDecimal value) {
        return value == null ? null : strip(value.setScale(1, RoundingMode.HALF_UP)) + "%";
    }

    private static String times(BigDecimal value) {
        return strip(value.setScale(1, RoundingMode.HALF_UP)) + "배";
    }

    private static String comma(BigDecimal value) {
        return String.format("%,d", value.setScale(0, RoundingMode.HALF_UP).longValue());
    }

    private static String strip(BigDecimal value) {
        BigDecimal s = value.stripTrailingZeros();
        return s.scale() <= 0 ? s.toBigInteger().toString() : s.toPlainString();
    }

    /** 출처 번호를 달기 전의 지표 */
    record Draft(String label, String note, List<Point> points) {
    }

    record Point(String period, String periodType, String value) {
    }
}
