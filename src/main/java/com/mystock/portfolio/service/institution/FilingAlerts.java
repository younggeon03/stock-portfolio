package com.mystock.portfolio.service.institution;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 새 13F 알림의 글. 텔레그램과 피드가 같은 글을 쓴다. DB 를 모르는 순수 함수라 테스트로 고정한다.
 *
 * 글은 평문이다. 텔레그램 HTML·마크다운 모드는 회사 이름의 &·< 같은 글자를 따로 처리해야 해서
 * 하나만 빠뜨려도 발송이 통째로 실패한다. 평문이면 그럴 일이 없다.
 */
public final class FilingAlerts {

    private FilingAlerts() {
    }

    /** 종류마다 몇 개까지 적나. 알림은 짧아야 읽힌다 */
    static final int PER_KIND = 3;

    public static String title(String institution, LocalDate period) {
        return institution + " " + quarter(period) + " 13F 공개";
    }

    public static String body(String institution, InstitutionPortfolioService.ChangesView changes,
                              LocalDate period, LocalDate filedDate, long cik, String baseUrl) {
        StringBuilder sb = new StringBuilder();
        sb.append(title(institution, period)).append(" (").append(filedDate).append(" 공개)\n");
        if (changes == null) {
            sb.append("비교할 앞 분기가 없습니다.\n");
        } else {
            sb.append("앞 분기(").append(quarter(changes.previousPeriod())).append(")와 비교, 주식 수 기준\n");
            line(sb, "새로 삼", changes.changes(), HoldingDiff.Kind.NEW, false);
            line(sb, "늘림", changes.changes(), HoldingDiff.Kind.ADDED, true);
            line(sb, "줄임", changes.changes(), HoldingDiff.Kind.REDUCED, true);
            line(sb, "다 팖", changes.changes(), HoldingDiff.Kind.SOLD_OUT, false);
        }
        sb.append("자세히: ").append(trimSlash(baseUrl)).append("/public/institution.html?cik=").append(cik)
                .append("&period=").append(period).append('\n');
        sb.append("분기말 기준이며 최대 45일 늦은 공개 자료입니다. 매매 권유가 아닙니다.");
        return sb.toString();
    }

    /** 공개된 지 며칠 안 된 것만 알린다. 처음 8분기를 받을 때 옛 분기가 쏟아지지 않게 */
    public static boolean worthAlerting(LocalDate filedDate, int holdingCount, LocalDate today, int maxAgeDays) {
        return holdingCount > 0 && !filedDate.isBefore(today.minusDays(maxAgeDays));
    }

    static String quarter(LocalDate period) {
        return period.getYear() + "년 " + ((period.getMonthValue() + 2) / 3) + "분기";
    }

    private static void line(StringBuilder sb, String label, List<HoldingDiff.Change> all, HoldingDiff.Kind kind,
                             boolean withPercent) {
        List<HoldingDiff.Change> picked = all.stream().filter(c -> c.kind() == kind).toList();
        if (picked.isEmpty()) {
            return;
        }
        String items = picked.stream().limit(PER_KIND).map(c -> {
            String name = c.ticker() != null ? c.ticker() : c.name();
            if (withPercent && c.sharesChangePercent() != null) {
                String sign = c.sharesChangePercent().signum() > 0 ? "+" : "";
                return name + " " + sign + c.sharesChangePercent().setScale(0, java.math.RoundingMode.HALF_UP) + "%";
            }
            return name;
        }).collect(Collectors.joining(", "));
        sb.append(label).append(": ").append(items);
        if (picked.size() > PER_KIND) {
            sb.append(" 외 ").append(picked.size() - PER_KIND);
        }
        sb.append('\n');
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;
}
