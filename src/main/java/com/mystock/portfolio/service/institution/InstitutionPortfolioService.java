package com.mystock.portfolio.service.institution;

import com.mystock.portfolio.domain.CusipTicker;
import com.mystock.portfolio.domain.CusipTickerRepository;
import com.mystock.portfolio.domain.Filing13F;
import com.mystock.portfolio.domain.Filing13FRepository;
import com.mystock.portfolio.domain.Holding13F;
import com.mystock.portfolio.domain.Holding13FRepository;
import com.mystock.portfolio.domain.Institution;
import com.mystock.portfolio.domain.InstitutionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 받아 둔 13F 를 화면용으로 꺼낸다. 바깥을 부르지 않으므로 0원이고 빠르다.
 *
 * 비중은 그 기관의 13F 합계 대비다. 13F 에는 미국 상장 주식·옵션만 들어가서
 * 기관 전체 자산 대비가 아니다. 국민연금이면 "국민연금 미국 주식 중 몇 %" 다.
 */
@Service
@Transactional(readOnly = true)
public class InstitutionPortfolioService {

    private final InstitutionRepository institutions;
    private final Filing13FRepository filings;
    private final Holding13FRepository holdings;
    private final CusipTickerRepository tickers;

    public InstitutionPortfolioService(InstitutionRepository institutions, Filing13FRepository filings,
                                       Holding13FRepository holdings, CusipTickerRepository tickers) {
        this.institutions = institutions;
        this.filings = filings;
        this.holdings = holdings;
        this.tickers = tickers;
    }

    /** 기관 목록. 받아 둔 최신 분기 요약을 붙인다 */
    public List<InstitutionView> list() {
        return institutions.findByActiveTrueOrderBySortOrder().stream()
                .map(inst -> {
                    List<Filing13F> fs = visible(inst.getCik());
                    return InstitutionView.of(inst, fs.isEmpty() ? null : fs.get(0),
                            fs.stream().map(Filing13F::getReportPeriod).toList());
                })
                .toList();
    }

    /** 한 기관의 한 분기 보유. period 가 없으면 최신 분기 */
    public Optional<HoldingsView> holdings(long cik, LocalDate period) {
        Optional<Institution> inst = institutions.findById(cik);
        if (inst.isEmpty()) {
            return Optional.empty();
        }
        List<Filing13F> fs = visible(cik);
        Optional<Filing13F> filing = period == null ? fs.stream().findFirst()
                : fs.stream().filter(f -> f.getReportPeriod().equals(period)).findFirst();
        if (filing.isEmpty()) {
            return Optional.empty();
        }

        List<Holding13F> rows = holdings.findByAccessionNoOrderByValueUsdDesc(filing.get().getAccessionNo());
        Map<String, CusipTicker> known = tickers.findAllById(rows.stream().map(Holding13F::getCusip).distinct().toList())
                .stream().collect(Collectors.toMap(CusipTicker::getCusip, Function.identity()));
        long total = filing.get().getTotalValueUsd();

        List<HoldingsView.Row> out = rows.stream().map(h -> {
            CusipTicker t = known.get(h.getCusip());
            return new HoldingsView.Row(h.getCusip(), t == null ? null : t.getTicker(), h.getIssuerName(),
                    h.getTitleOfClass(), h.getPutCall().isEmpty() ? null : h.getPutCall(),
                    h.getShares(), h.getValueUsd(), percent(h.getValueUsd(), total));
        }).toList();

        return Optional.of(new HoldingsView(InstitutionView.of(inst.get(), filing.get(),
                fs.stream().map(Filing13F::getReportPeriod).toList()),
                filing.get().getReportPeriod(), filing.get().getFiledDate(), total, out));
    }

    /**
     * 보유가 있는 분기만, 최신부터.
     * 비공개로 낸 빈 제출(노르웨이 중앙은행 1·3분기)을 "최신 분기" 로 고르면 보유가 텅 빈 화면이 된다
     */
    private List<Filing13F> visible(long cik) {
        return filings.findByCikOrderByReportPeriodDesc(cik).stream()
                .filter(f -> f.getHoldingCount() > 0)
                .toList();
    }

    static BigDecimal percent(long part, long whole) {
        if (whole == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(part).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(whole), 2, RoundingMode.HALF_UP);
    }

    /** 기관 한 곳의 요약 */
    public record InstitutionView(long cik, String nameKo, String name, String manager, String note,
                                  LocalDate latestPeriod, LocalDate filedDate, Long totalValueUsd,
                                  Integer holdingCount, List<LocalDate> periods) {

        static InstitutionView of(Institution inst, Filing13F latest, List<LocalDate> periods) {
            return new InstitutionView(inst.getCik(), inst.getNameKo(), inst.getName(), inst.getManager(),
                    inst.getNote(),
                    latest == null ? null : latest.getReportPeriod(),
                    latest == null ? null : latest.getFiledDate(),
                    latest == null ? null : latest.getTotalValueUsd(),
                    latest == null ? null : latest.getHoldingCount(),
                    periods);
        }
    }

    /** 한 분기 보유 전체 */
    public record HoldingsView(InstitutionView institution, LocalDate period, LocalDate filedDate,
                               long totalValueUsd, List<Row> holdings) {

        /** ticker 가 null 이면 아직 못 찾았거나 원래 없는 증권(채권·워런트). putCall 이 null 이면 주식 */
        public record Row(String cusip, String ticker, String issuerName, String titleOfClass, String putCall,
                          long shares, long valueUsd, BigDecimal weightPercent) {
        }
    }
}
