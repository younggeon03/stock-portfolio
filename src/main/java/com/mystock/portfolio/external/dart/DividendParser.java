package com.mystock.portfolio.external.dart;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "현금ㆍ현물배당결정" 공시 본문(태그를 걷어낸 글자)에서 배당 정보를 꺼낸다.
 *
 * 공시는 정해진 양식의 표라 "칸 이름 값" 순서가 일정하다. 예:
 *   배당구분 분기배당 2. 배당종류 현금배당 ... 3. 1주당 배당금(원) 보통주식 374 종류주식 374 ...
 *   6. 배당기준일 2026-06-30 7. 배당금지급 예정일자 2026-08-28 ...
 *
 * ★ 비어 있을 수 있는 것
 * 결산배당은 주주총회 전에 결정 공시가 나오면 지급 예정일이 "-" 다. 우선주가 없으면 종류주식도 "-".
 * 비어 있으면 null 로 두고 화면이 "미정" 으로 보인다. 지어내지 않는다.
 */
public final class DividendParser {

    private DividendParser() {
    }

    public record Dividend(String kind, String cashType, BigDecimal perShareCommon, BigDecimal perSharePreferred,
                           BigDecimal yieldCommon, LocalDate recordDate, LocalDate payDate, LocalDate boardDate) {
    }

    /** 날짜: 2026-06-30, 2026.06.30, 2026년 06월 30일 */
    private static final String DATE = "(\\d{4})\\s*[-.년]\\s*(\\d{1,2})\\s*[-.월]\\s*(\\d{1,2})\\s*일?";

    private static final Pattern KIND = Pattern.compile("배당구분\\s+(결산배당|분기배당|중간배당|[^\\s\\d]+배당)");
    private static final Pattern CASH_TYPE = Pattern.compile("배당종류\\s+(현금배당|주식배당|현물배당|[^\\s\\d]+배당)");
    private static final Pattern PER_SHARE = Pattern.compile(
            "1주당\\s*배당금\\s*\\(원\\)\\s*보통주식?\\s+([\\d,]+|-)(?:\\s*종류주식?\\s+([\\d,]+|-))?");
    private static final Pattern YIELD = Pattern.compile("시가배당[률율]\\s*\\(%\\)\\s*보통주식?\\s+([\\d.]+|-)");
    private static final Pattern RECORD = Pattern.compile("배당기준일\\s+" + DATE);
    private static final Pattern PAY = Pattern.compile("배당금\\s*지급\\s*예정\\s*일자\\s+(?:" + DATE + "|-)");
    private static final Pattern BOARD = Pattern.compile("이사회\\s*결의일\\s*\\(\\s*결정일\\s*\\)\\s+" + DATE);

    /** 배당기준일이나 보통주 주당 배당금이 없으면 배당결정 공시로 볼 수 없다 → 빈 값 */
    public static Optional<Dividend> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        LocalDate record = date(RECORD.matcher(text), 1);
        Matcher per = PER_SHARE.matcher(text);
        BigDecimal common = null;
        BigDecimal preferred = null;
        if (per.find()) {
            common = number(per.group(1));
            preferred = per.groupCount() >= 2 ? number(per.group(2)) : null;
        }
        if (record == null || common == null) {
            return Optional.empty();
        }
        return Optional.of(new Dividend(
                group(KIND.matcher(text)),
                group(CASH_TYPE.matcher(text)),
                common, preferred,
                dividendYield(text),
                record,
                date(PAY.matcher(text), 1),
                date(BOARD.matcher(text), 1)));
    }

    private static BigDecimal dividendYield(String text) {
        Matcher m = YIELD.matcher(text);
        return m.find() ? number(m.group(1)) : null;
    }

    private static String group(Matcher m) {
        return m.find() ? m.group(1) : null;
    }

    private static LocalDate date(Matcher m, int first) {
        if (!m.find() || m.group(first) == null) {
            return null;
        }
        try {
            return LocalDate.of(Integer.parseInt(m.group(first)), Integer.parseInt(m.group(first + 1)),
                    Integer.parseInt(m.group(first + 2)));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static BigDecimal number(String s) {
        if (s == null || s.isBlank() || "-".equals(s.strip())) {
            return null;
        }
        try {
            return new BigDecimal(s.replace(",", "").strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
