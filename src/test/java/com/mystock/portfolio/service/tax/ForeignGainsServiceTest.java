package com.mystock.portfolio.service.tax;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mystock.portfolio.brokerage.Broker;
import com.mystock.portfolio.external.namuh.dto.NamuhPeriodPnlResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 증권사를 합친 해외주식 양도소득. 증권사 API 는 부르지 않고 가짜 소스를 넣는다. 금액은 데모 값.
 */
class ForeignGainsServiceTest {

    private static final Clock OCT_19 = Clock.fixed(Instant.parse("2026-10-19T03:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Test
    void 두_증권사_손익을_합쳐서_공제_250만원을_한_번만_뺀다() {
        var service = new ForeignGainsService(List.of(
                source(Broker.TOSS, true, "1800000"),
                source(Broker.NAMUH, true, "1200000")), OCT_19);

        ForeignGainsView v = service.compute(2026);

        // 합계 300만 - 공제 250만 = 50만 × 22% = 11만. 증권사마다 따로 보면 둘 다 공제 안이라 0원으로 보인다
        assertThat(v.realizedKrw()).isEqualByComparingTo("3000000");
        assertThat(v.taxableKrw()).isEqualByComparingTo("500000");
        assertThat(v.estimatedTaxKrw()).isEqualByComparingTo("110000");
        assertThat(v.remainingDeductionKrw()).isEqualByComparingTo("0");
        assertThat(v.to()).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(v.partial()).isFalse();
    }

    @Test
    void 한쪽_손실이_다른쪽_이익을_깎는다() {
        var service = new ForeignGainsService(List.of(
                source(Broker.TOSS, true, "-1000000"),
                source(Broker.NAMUH, true, "2000000")), OCT_19);

        ForeignGainsView v = service.compute(2026);

        assertThat(v.realizedKrw()).isEqualByComparingTo("1000000");
        assertThat(v.estimatedTaxKrw()).isEqualByComparingTo("0");
        assertThat(v.remainingDeductionKrw()).isEqualByComparingTo("1500000");
    }

    @Test
    void 연결_안_한_증권사는_빠짐이_아니고_실패한_곳만_반쪽_표시다() {
        var onlyNamu = new ForeignGainsService(List.of(
                source(Broker.TOSS, false, "999"),
                source(Broker.NAMUH, true, "500000")), OCT_19).compute(2026);
        assertThat(onlyNamu.brokers()).extracting(ForeignGainsView.BrokerLine::broker).containsExactly("NAMUH");
        assertThat(onlyNamu.partial()).isFalse();

        var tossFails = new ForeignGainsService(List.of(
                failing(Broker.TOSS),
                source(Broker.NAMUH, true, "500000")), OCT_19).compute(2026);
        assertThat(tossFails.partial()).isTrue();
        assertThat(tossFails.realizedKrw()).isEqualByComparingTo("500000");
        assertThat(tossFails.brokers().get(0).error()).contains("403");
    }

    @Test
    void 나무_기간손익_응답을_원화_합계로_읽고_판_게_없는_해는_0이다() throws Exception {
        String json = """
                {"Output_0":{"fc_sll_amt_sum":5000000,"fc_sdr_xps_sum":3000,"fc_rzt_pls_sum":412000},
                 "Output_1":[{"orr_dt":"20260302","sll_qty":3,"fc_sll_amt":5000000,"fc_rzt_pls":412000},
                             {"orr_dt":"20260303","sll_qty":0,"fc_sll_amt":0,"fc_rzt_pls":0}],
                 "rsp_cd":"00000","rsp_msg":"조회가 완료되었습니다"}""";
        var r = new ObjectMapper().readValue(json, NamuhPeriodPnlResponse.class);

        var g = NamuhForeignGainSource.toGains(r);
        assertThat(g.realizedKrw()).isEqualByComparingTo("412000");
        assertThat(g.sales()).singleElement().extracting(ForeignGainSource.Sale::date).isEqualTo(LocalDate.of(2026, 3, 2));

        var empty = new ObjectMapper().readValue("{\"rsp_cd\":\"00310\",\"rsp_msg\":\"조회할 내용이 없습니다\"}",
                NamuhPeriodPnlResponse.class);
        assertThat(empty.isSuccess()).isTrue();
        assertThat(NamuhForeignGainSource.toGains(empty).realizedKrw()).isEqualByComparingTo("0");
    }

    private static ForeignGainSource source(Broker broker, boolean configured, String realized) {
        return new ForeignGainSource() {
            public Broker broker() { return broker; }
            public boolean isConfigured() { return configured; }
            public BrokerGains realized(LocalDate from, LocalDate to) {
                return new BrokerGains(new BigDecimal(realized), BigDecimal.ZERO, List.of(), List.of());
            }
        };
    }

    private static ForeignGainSource failing(Broker broker) {
        return new ForeignGainSource() {
            public Broker broker() { return broker; }
            public boolean isConfigured() { return true; }
            public BrokerGains realized(LocalDate from, LocalDate to) {
                throw new IllegalStateException("토스증권 토큰 발급 실패 (HTTP 403)");
            }
        };
    }
}
