package com.mystock.portfolio.external.filing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mystock.portfolio.domain.StoredFinancials;
import com.mystock.portfolio.domain.StoredFinancialsRepository;
import com.mystock.portfolio.external.dart.DartFinancialService;
import com.mystock.portfolio.external.edgar.EdgarFinancialService;
import com.mystock.portfolio.external.toss.TossMarketDataService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 공시 재무 저장. 재무(주가 없는 부분)는 DB 에 두고 하루 안에는 공시처를 다시 부르지 않는다.
 * PER·PBR 은 읽을 때 지금 주가로 잰다. 다시 받기가 실패하면 저장된 것을 쓴다. 숫자는 데모 값
 */
class FilingServiceStoreTest {

    private final DartFinancialService dart = mock(DartFinancialService.class);
    private final EdgarFinancialService edgar = mock(EdgarFinancialService.class);
    private final TossMarketDataService toss = mock(TossMarketDataService.class);
    private final StoredFinancialsRepository store = mock(StoredFinancialsRepository.class);
    private final ObjectMapper om = new ObjectMapper().findAndRegisterModules();
    private final FilingService service = new FilingService(dart, edgar, toss, store, om);

    /** EPS 10, BPS 50 인 회사. 주가 200 이면 PER 20, PBR 4 */
    private static CompanyFinancials demo() {
        CompanyFinancials.Period fy = new CompanyFinancials.Period("FY2025 (2025-12-31 결산)",
                BigDecimal.valueOf(1000), BigDecimal.valueOf(200), BigDecimal.valueOf(100),
                null, null, null, null, null, null, null, null,
                BigDecimal.valueOf(10), BigDecimal.valueOf(50), LocalDate.of(2025, 12, 31));
        return CompanyFinancials.of("DEMO CORP", "SEC EDGAR", "USD", "연결", List.of(fy), null, null,
                null, "보통주", List.of());
    }

    private StoredFinancials row(LocalDateTime fetchedAt) throws Exception {
        StoredFinancials r = new StoredFinancials("DEMO", "US");
        r.update(om.writeValueAsString(demo()), true, fetchedAt);
        return r;
    }

    @Test
    void 처음이면_공시처에서_주가_없이_받아_저장하고_PER_은_지금_주가로() {
        when(store.findById("DEMO")).thenReturn(Optional.empty());
        when(edgar.find(eq("DEMO"), any(), isNull())).thenReturn(Optional.of(demo()));
        when(toss.closesOnOrBefore(eq("DEMO"), anyList())).thenReturn(Map.of());

        CompanyFinancials f = service.find("DEMO", "US", false, null, BigDecimal.valueOf(200)).orElseThrow();

        assertThat(f.per()).isEqualByComparingTo("20");
        assertThat(f.pbr()).isEqualByComparingTo("4");
        ArgumentCaptor<StoredFinancials> saved = ArgumentCaptor.forClass(StoredFinancials.class);
        verify(store).save(saved.capture());
        // 저장본에는 주가로 잰 값이 없다
        assertThat(saved.getValue().getFinancialsJson()).contains("\"per\":null").contains("\"pbr\":null");
    }

    @Test
    void 하루_안이면_공시처를_다시_부르지_않는다() throws Exception {
        when(store.findById("DEMO")).thenReturn(Optional.of(row(LocalDateTime.now().minusHours(3))));

        CompanyFinancials f = service.find("DEMO", "US", false, null, BigDecimal.valueOf(300)).orElseThrow();

        assertThat(f.per()).isEqualByComparingTo("30");   // 주가가 바뀌면 PER 만 바뀐다
        verify(edgar, never()).find(any(), any(), any());
    }

    @Test
    void 하루가_지났는데_다시_받기가_실패하면_저장된_것을_쓴다() throws Exception {
        when(store.findById("DEMO")).thenReturn(Optional.of(row(LocalDateTime.now().minusDays(3))));
        when(edgar.find(eq("DEMO"), any(), isNull())).thenReturn(Optional.empty());

        Optional<CompanyFinancials> f = service.find("DEMO", "US", false, null, BigDecimal.valueOf(200));

        assertThat(f).isPresent();
        assertThat(f.get().corpName()).isEqualTo("DEMO CORP");
        verify(edgar).find(eq("DEMO"), any(), isNull());
    }

    @Test
    void 펀드는_부르지_않는다() {
        assertThat(service.find("SPY", "US", true, null, BigDecimal.ONE)).isEmpty();
        verify(store, never()).findById(any());
    }
}
