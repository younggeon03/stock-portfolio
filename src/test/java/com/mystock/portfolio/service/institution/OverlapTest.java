package com.mystock.portfolio.service.institution;

import com.mystock.portfolio.web.InstitutionControllerTestAccess;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 겹침 = Σ min(내 비중, 기관 비중). 숫자는 데모 값이고 기댓값은 손으로 계산했다.
 */
class OverlapTest {

    private static final List<Overlap.Theirs> BRK = List.of(
            theirs("AAPL", "22.00"), theirs("AXP", "17.00"), theirs("KO", "11.00"), theirs("BRK.B", "0.50"));

    @Test
    void 겹침은_종목마다_작은_쪽_비중의_합이다() {
        // 내 비중을 100% 로 맞추면 AAPL 50, KO 25, NVDA 25
        Overlap.Result r = Overlap.compare(
                List.of(new Overlap.Mine("AAPL", 2), new Overlap.Mine("KO", 1), new Overlap.Mine("NVDA", 1)), BRK, 5);

        // min(50,22) + min(25,11) = 33
        assertThat(r.overlapPercent()).isEqualByComparingTo("33.00");
        assertThat(r.shared()).extracting(Overlap.Shared::ticker).containsExactly("AAPL", "KO");
        assertThat(r.onlyMine()).extracting(Overlap.Mine::ticker).containsExactly("NVDA");
        // 그 기관 상위 종목 중 내게 없는 것
        assertThat(r.theirTopMissing()).extracting(Overlap.Theirs::ticker).containsExactly("AXP", "BRK.B");
    }

    @Test
    void 티커_표기가_달라도_같은_종목으로_본다() {
        Overlap.Result r = Overlap.compare(List.of(new Overlap.Mine("brk-b", 1)), BRK, 0);

        assertThat(r.shared()).singleElement().satisfies(s -> assertThat(s.ticker()).isEqualTo("BRK.B"));
        assertThat(r.overlapPercent()).isEqualByComparingTo("0.50");
    }

    @Test
    void 하나도_안_겹치면_0이고_입력이_비면_0이다() {
        assertThat(Overlap.compare(List.of(new Overlap.Mine("TSLA", 10)), BRK, 0).overlapPercent())
                .isEqualByComparingTo("0");
        assertThat(Overlap.compare(List.of(), BRK, 0).overlapPercent()).isEqualByComparingTo("0");
    }

    @Test
    void 같은_포트폴리오면_100이다() {
        List<Overlap.Theirs> same = List.of(theirs("AAPL", "60"), theirs("MSFT", "40"));
        Overlap.Result r = Overlap.compare(List.of(new Overlap.Mine("AAPL", 60), new Overlap.Mine("MSFT", 40)), same, 0);

        assertThat(r.overlapPercent()).isEqualByComparingTo("100");
    }

    @Test
    void 주소의_입력을_읽고_모양이_틀리면_무엇이_틀렸는지_알린다() {
        assertThat(InstitutionControllerTestAccess.parse("AAPL:30, nvda:20%,KO"))
                .extracting(Overlap.Mine::ticker, Overlap.Mine::weight)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("AAPL", 30.0),
                        org.assertj.core.groups.Tuple.tuple("nvda", 20.0),
                        org.assertj.core.groups.Tuple.tuple("KO", 1.0));
        assertThatThrownBy(() -> InstitutionControllerTestAccess.parse("AAPL:abc")).hasMessageContaining("숫자");
        assertThatThrownBy(() -> InstitutionControllerTestAccess.parse("<script>:1")).hasMessageContaining("티커");
        assertThatThrownBy(() -> InstitutionControllerTestAccess.parse("AAPL:-5")).hasMessageContaining("0보다");
        assertThatThrownBy(() -> InstitutionControllerTestAccess.parse("A,".repeat(51))).hasMessageContaining("50개");
    }

    private static Overlap.Theirs theirs(String ticker, String weight) {
        return new Overlap.Theirs(ticker, ticker + " INC", new BigDecimal(weight));
    }
}
