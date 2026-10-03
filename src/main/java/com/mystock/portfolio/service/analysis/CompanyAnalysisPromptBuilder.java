package com.mystock.portfolio.service.analysis;

import com.mystock.portfolio.common.AppException;
import com.mystock.portfolio.external.filing.CompanyFinancials;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 클로드에게 보낼 프롬프트를 조립한다.
 *
 * 시스템 프롬프트(역할·규칙)는 리소스 파일에서 그대로 읽어오고,
 * 사용자 프롬프트(종목별 사실)는 여기서 만든다.
 */
@Component
public class CompanyAnalysisPromptBuilder {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 시스템 프롬프트는 한 번만 읽어서 재사용한다 */
    private volatile String systemPrompt;

    /** 역할과 규칙. 모든 종목에 공통 */
    public String systemPrompt() {
        if (systemPrompt == null) {
            synchronized (this) {
                if (systemPrompt == null) {
                    systemPrompt = readResource("prompts/company-analysis-system.md");
                }
            }
        }
        return systemPrompt;
    }

    /** 종목별 요청문 */
    public String userPrompt(CompanyAnalysisFacts f) {
        StringBuilder sb = new StringBuilder();

        sb.append("[분석 대상]\n");
        sb.append("종목명: ").append(f.name()).append('\n');
        if (f.englishName() != null && !f.englishName().isBlank()) {
            sb.append("영문명: ").append(f.englishName()).append('\n');
        }
        sb.append("종목코드: ").append(f.symbol()).append('\n');
        sb.append("시장: ").append(f.market()).append(" (").append(f.marketCountry()).append(")\n");
        sb.append("거래통화: ").append(f.currency()).append('\n');
        sb.append("종목유형: ").append(f.securityType());
        if (f.leverageFactor() != null) {
            sb.append("  ※ 레버리지 ").append(strip(f.leverageFactor())).append("배 상품이다");
        }
        sb.append('\n');
        if (f.sharesOutstanding() != null) {
            sb.append("발행주식수: ").append(comma(f.sharesOutstanding())).append('\n');
        }

        sb.append('\n').append(searchGuide(f)).append('\n');

        if (!f.held()) {
            // 안 가진 종목. 규칙(판정을 "새로 담을 만한가" 로, POSITION_REVIEW 는 해당 없음)은 시스템 지시문
            // (company-analysis-system.md)에 한 번만 둔다. 여기서는 그 규칙을 켜는 표시만 한다
            sb.append("\n[보유현황] 보유하지 않은 종목이다. 평단가·수량·손익·비중이 없다.\n");
            sb.append("조회 기준: ").append(f.asOf().format(TIME)).append('\n');
            sb.append("현재가: ").append(money(f.lastPrice(), f.currency())).append('\n');
        } else {
            appendPosition(sb, f);
        }
        appendMarket(sb, f);
        return sb.toString();
    }

    /** 가진 종목의 보유 현황 */
    private void appendPosition(StringBuilder sb, CompanyAnalysisFacts f) {
        sb.append("\n[보유현황] ★ 아래 숫자는 앱이 증권사 API 에서 직접 가져온 확정값이다. 그대로 인용하고 바꾸지 마라.\n");
        sb.append("조회 기준: ").append(f.asOf().format(TIME)).append(" (토스증권·나무증권 합산)\n");
        sb.append("보유처: ").append(f.brokers()).append('\n');
        sb.append("보유 수량: ").append(strip(f.quantity())).append("주\n");
        sb.append("현재가: ").append(money(f.lastPrice(), f.currency())).append('\n');
        if (f.averagePurchasePrice() != null) {
            sb.append("내 평단가: ").append(money(f.averagePurchasePrice(), f.currency())).append('\n');
            sb.append("평단가 대비 현재가: ").append(signed(f.priceGapPercent())).append("%\n");
        }
        sb.append("평가금액: ").append(comma(f.marketValueKrw())).append("원\n");
        sb.append("매입금액: ").append(comma(f.purchaseKrw())).append("원\n");
        sb.append("평가손익: ").append(signedComma(f.profitLossKrw())).append("원 (")
                .append(signed(f.profitRatePercent())).append("%)\n");
        sb.append("전체 자산에서 이 종목의 비중: ").append(strip(f.weightPercent())).append("%\n");
        sb.append("전체 자산: ").append(comma(f.totalValueKrw())).append("원\n");
    }

    /** 시세 통계·공시 재무·위험 신호·요청. 가진 종목이든 아니든 같다 */
    private void appendMarket(StringBuilder sb, CompanyAnalysisFacts f) {
        if (f.annualizedVolatilityPercent() != null) {
            sb.append("\n[시세 통계] 최근 ").append(f.dataPoints()).append("거래일, 토스증권 일봉 기준\n");
            sb.append("연환산 변동성: ").append(strip(f.annualizedVolatilityPercent())).append("%");
            if (f.dailyVolatilityPercent() != null) {
                sb.append("  (하루 평균 등락 ").append(strip(f.dailyVolatilityPercent())).append("%)");
            }
            sb.append('\n');
            sb.append("기간 수익률: ").append(signed(f.periodReturnPercent())).append("%\n");
            if (f.periodHigh() != null && f.periodLow() != null) {
                sb.append("기간 최고 종가: ").append(money(f.periodHigh(), f.currency()))
                        .append(" / 최저 종가: ").append(money(f.periodLow(), f.currency())).append('\n');
            }
        }

        if (f.financials() != null) {
            sb.append('\n').append(financials(f.financials(), f.lastPrice()));
        }

        if (f.riskFlags() != null && !f.riskFlags().isEmpty()) {
            sb.append("\n[위험 신호] ★ 앱이 확정 데이터로 판정했다. risks 에 반드시 반영해라.\n");
            for (String flag : f.riskFlags()) {
                sb.append("- ").append(flag).append('\n');
            }
        }

        sb.append("\n[요청]\n");
        sb.append("위 7개 섹션을 모두 채워서 submit_analysis 도구를 정확히 한 번 호출해라.\n");
        if (f.held()) {
            sb.append("POSITION_REVIEW 에서는 [보유현황] 의 숫자를 직접 인용해라.\n");
            sb.append("risks 에는 이 보유 상태에서 실제로 감당 중인 위험을 담아라.\n");
        } else {
            sb.append("보유하지 않은 종목이다. 지시문의 \"보유하지 않은 종목\" 규칙을 따라라.\n");
        }
    }

    /**
     * 공시 재무(DART 또는 SEC EDGAR)를 표로 넣는다.
     *
     * ★ 표로 넣는 이유
     * 모델이 연도끼리 비교해 "좋아지는 중인지" 를 말해야 한다. 문장으로 흩어 두면 같은 항목을 가로로 못 읽는다.
     * 금액은 억원·백만 달러로 줄인다. 원 단위 15자리 숫자는 자릿수를 잘못 읽기 쉽다.
     */
    String financials(CompanyFinancials d, BigDecimal price) {
        boolean usd = "USD".equals(d.currency());
        StringBuilder sb = new StringBuilder();
        sb.append("[공시 재무] ★ ").append(d.filer()).append(" 에 공시된 ").append(d.statementKind())
                .append("재무제표 원본과 그걸로 앱이 계산한 비율이다. 확정값이니 그대로 인용하고 웹에서 다시 찾지 마라.\n");
        sb.append(usd ? "금액 단위: 백만 달러. EPS·BPS 는 달러." : "금액 단위: 억원. EPS·BPS 는 원.").append('\n');
        if (d.shareBasis() != null) {
            sb.append("주당 지표는 ").append(d.shareBasis()).append(" 로 나눴다. 모든 연도에 같은 주식수를 썼다.\n");
        }
        if (usd) {
            sb.append("미국 회사는 회계연도가 달력과 다를 수 있다. 기간 이름의 결산일을 기준으로 읽어라.\n");
        }

        List<CompanyFinancials.Period> rows = new ArrayList<>(d.annual());
        if (d.interim() != null) {
            rows.add(d.interim());
        }
        if (d.ttm() != null) {
            rows.add(d.ttm());
        }
        // 지배순이익 열은 값이 있을 때만 넣는다. 빈 열이 있으면 모델이 "모름" 으로 읽는다
        boolean owners = hasOwners(d);
        sb.append("| 기간 | 매출 | 영업이익 | 순이익 | ").append(owners ? "지배순이익 | " : "")
                .append("자산 | 부채 | 자본 | 부채비율 | 영업이익률 | ROE | EPS | BPS |\n");
        for (CompanyFinancials.Period p : rows) {
            sb.append("| ").append(p.label())
                    .append(" | ").append(scaled(p.revenue(), usd))
                    .append(" | ").append(scaled(p.operatingIncome(), usd))
                    .append(" | ").append(scaled(p.netIncome(), usd));
            if (owners) {
                sb.append(" | ").append(scaled(p.ownersNetIncome(), usd));
            }
            sb.append(" | ").append(scaled(p.totalAssets(), usd))
                    .append(" | ").append(scaled(p.totalLiabilities(), usd))
                    .append(" | ").append(scaled(p.totalEquity(), usd))
                    .append(" | ").append(pct(p.debtRatio()))
                    .append(" | ").append(pct(p.operatingMargin()))
                    .append(" | ").append(pct(p.roe()))
                    .append(" | ").append(perShare(p.eps(), usd))
                    .append(" | ").append(perShare(p.bps(), usd))
                    .append(" |\n");
        }
        if (d.ttm() == null) {
            sb.append("올해 분기·반기 보고서는 아직 없다. 재무상태(자산·부채·자본)는 가장 최근 결산 기준이다.\n");
        } else {
            sb.append("최근 4분기 = 작년 연간 + 올해 누적 − 작년 같은 기간 누적. 누적 줄의 ROE·EPS 는 1년치가 아니라 비웠다.\n");
        }

        sb.append("현재가 ").append(money(price, d.currency())).append(" 기준 ");
        sb.append("PER ").append(d.per() == null ? "계산 불가(적자이거나 이익 없음)" : strip(d.per()) + "배");
        sb.append(", PBR ").append(d.pbr() == null ? "계산 불가" : strip(d.pbr()) + "배").append('\n');
        if (hasOwners(d)) {
            sb.append("순이익은 비지배지분을 포함한 연결 순이익이고, 지배순이익은 지배기업 주주 몫이다. ")
                    .append("ROE·EPS·BPS·PER·PBR 은 지배주주 몫으로 계산했다. 적정가도 지배주주 몫 기준으로 잡아라.\n");
        } else if (!usd && "연결".equals(d.statementKind())) {
            sb.append("순이익은 비지배지분을 포함한 연결 당기순이익이다. 지배주주 순이익과 차이가 크면 그 점을 밝혀라.\n");
        }

        sb.append("출처로 쓸 공시 원문 (sources 에 그대로 넣어라):\n");
        for (CompanyFinancials.Source source : d.sources()) {
            sb.append("- ").append(source.title()).append(": ").append(source.url()).append('\n');
        }
        if (d.history() != null && !d.history().isEmpty()) {
            sb.append("\n[과거 배수] ★ 결산일 종가(토스) ÷ 그 해 공시 EPS·BPS. 이 회사가 받아온 배수 범위다.\n");
            sb.append("| 기간 | 종가 기준일 | 종가 | PER | PBR |\n");
            for (CompanyFinancials.Valuation v : d.history()) {
                sb.append("| ").append(v.label())
                        .append(" | ").append(v.tradeDate())
                        .append(" | ").append(money(v.close(), d.currency()))
                        .append(" | ").append(v.per() == null ? "적자" : strip(v.per()) + "배")
                        .append(" | ").append(v.pbr() == null ? "-" : strip(v.pbr()) + "배")
                        .append(" |\n");
            }
            sb.append("과거 EPS 도 지금 주식수로 나눴다. 그 사이 소각·증자가 컸다면 과거 배수가 조금 어긋난다.\n");
            sb.append("적정가의 PER 범위는 이 표에서 잡아라. 웹에서는 컨센서스 전망치만 찾아라.\n");
        } else {
            sb.append("과거 PER 범위를 구할 과거 주가는 여기에 없다. 그 범위와 컨센서스 전망치는 웹에서 찾아라.\n");
        }
        return sb.toString();
    }

    /**
     * 큰 금액을 읽을 수 있는 단위로 줄인다. 원은 억원, 달러는 백만 달러.
     * 100 미만은 소수 한 자리까지 남긴다. 작은 회사가 0 으로 뭉개지면 안 된다.
     */
    private String scaled(BigDecimal amount, boolean usd) {
        if (amount == null) {
            return "-";
        }
        BigDecimal unit = BigDecimal.valueOf(usd ? 1_000_000L : 100_000_000L);
        BigDecimal value = amount.divide(unit, 1, java.math.RoundingMode.HALF_UP);
        if (value.abs().compareTo(BigDecimal.valueOf(100)) >= 0) {
            return comma(value);
        }
        return value.toPlainString();
    }

    /** 주당 지표. 원은 정수, 달러는 센트까지 */
    private String perShare(BigDecimal value, boolean usd) {
        if (value == null) {
            return "-";
        }
        return usd ? value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() : comma(value);
    }

    /** 한 기간이라도 지배주주 순이익이 있으면 그걸로 계산한 것이다 */
    private boolean hasOwners(CompanyFinancials d) {
        return d.annual().stream().anyMatch(p -> p.ownersNetIncome() != null)
                || (d.ttm() != null && d.ttm().ownersNetIncome() != null);
    }

    private String pct(BigDecimal value) {
        return value == null ? "-" : strip(value) + "%";
    }

    /**
     * 어디를 뒤져야 하는지 알려준다.
     * 국내 종목과 미국 종목은 자료가 있는 곳이 완전히 다르다.
     */
    private String searchGuide(CompanyAnalysisFacts f) {
        if ("KR".equals(f.marketCountry()) && f.financials() != null) {
            return """
                    [검색 가이드]
                    한국 상장 종목이고 종목코드는 한국거래소 6자리 코드다.
                    과거 재무는 아래 [공시 재무] 에 DART 원본으로 들어 있다. 재무제표를 검색하느라 검색 횟수를 쓰지 마라.
                    검색은 전망과 의견에만 써라: 한경컨센서스·증권사 리포트의 컨센서스와 목표주가, 회사 IR 가이던스,
                    과거 PER·PBR 범위, 사업부별 매출 구성, 산업 동향과 최근 뉴스.
                    검색어에 종목명과 코드를 함께 넣어라. 예: "%s %s 컨센서스", "%s 실적 전망"
                    ★ 종목명과 코드가 모두 일치하는 회사인지 먼저 확인해라. 비슷한 이름의 다른 회사와 헷갈리면 안 된다."""
                    .formatted(f.name(), f.symbol(), f.name());
        }
        if ("KR".equals(f.marketCountry())) {
            return """
                    [검색 가이드]
                    한국 상장 종목이고 종목코드는 한국거래소 6자리 코드다.
                    DART 전자공시(사업보고서·분기보고서), 회사 IR, 한경컨센서스, 증권사 리포트,
                    네이버페이 증권을 우선 검색해라.
                    검색어에 종목명과 코드를 함께 넣어라. 예: "%s %s 사업보고서 재무제표", "%s 실적 컨센서스"
                    ★ 종목명과 코드가 모두 일치하는 회사인지 먼저 확인해라. 비슷한 이름의 다른 회사와 헷갈리면 안 된다."""
                    .formatted(f.name(), f.symbol(), f.name());
        }
        if (f.financials() != null) {
            return """
                    [검색 가이드]
                    미국 상장 종목이다. 과거 재무는 아래 [공시 재무] 에 SEC EDGAR 원본으로 들어 있다.
                    재무제표를 검색하느라 검색 횟수를 쓰지 마라.
                    검색은 전망과 의견에만 써라: 애널리스트 컨센서스와 목표주가, 회사 가이던스(실적 발표·IR),
                    과거 PER 범위, 사업부별 매출 구성, 산업 동향과 최근 뉴스.
                    검색어는 영어로 해도 되지만 최종 답변은 한국어로 쓴다.""";
        }
        return """
                [검색 가이드]
                미국 상장 종목이다. SEC EDGAR(10-K, 10-Q), 회사 IR 페이지, 발행사 공식 상품 페이지,
                주요 금융 매체를 우선 검색해라.
                검색어는 영어로 해도 되지만 최종 답변은 한국어로 쓴다.""";
    }

    private String readResource(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AppException("프롬프트 파일을 읽지 못했습니다: " + path, e);
        }
    }

    /** 1234567 → "1,234,567" */
    private String comma(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        return String.format("%,d", value.setScale(0, java.math.RoundingMode.HALF_UP).longValue());
    }

    /** 부호를 붙인 금액 */
    private String signedComma(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        String text = comma(value.abs());
        return value.signum() < 0 ? "-" + text : "+" + text;
    }

    /** 부호를 붙인 비율 */
    private String signed(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        return (value.signum() > 0 ? "+" : "") + strip(value);
    }

    /** 통화 기호를 붙인다 */
    private String money(BigDecimal value, String currency) {
        if (value == null) {
            return "-";
        }
        if ("USD".equals(currency)) {
            return "$" + value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
        }
        return comma(value) + "원";
    }

    /** 뒤에 붙은 0 을 없앤다. 0.034888000 → 0.034888 */
    private String strip(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() <= 0 ? stripped.toBigInteger().toString() : stripped.toPlainString();
    }
}
