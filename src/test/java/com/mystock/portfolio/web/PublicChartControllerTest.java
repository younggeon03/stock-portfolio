package com.mystock.portfolio.web;

import com.mystock.portfolio.common.AppException;
import com.mystock.portfolio.service.TossAnalysisService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 공개 차트 주소. 로그인 없이 열리므로 종목 모양만 받고, 토스 오류 문구(서버 IP 가 들어 있다)를 내보내지 않는다.
 */
class PublicChartControllerTest {

    private final TossAnalysisService service = mock(TossAnalysisService.class);
    private final PublicChartController controller = new PublicChartController(service);

    @Test
    void 종목_모양이_아니면_토스를_부르지_않고_400() {
        assertThat(controller.chart("../etc/passwd").getStatusCode().value()).isEqualTo(400);
        assertThat(controller.chart("ABCDEFGHIJKL").getStatusCode().value()).isEqualTo(400);
        verify(service, never()).chart(anyString(), anyInt());
    }

    @Test
    void 미국_티커와_국내_6자리는_받는다() {
        assertThat(PublicChartController.SYMBOL.matcher("MSFT").matches()).isTrue();
        assertThat(PublicChartController.SYMBOL.matcher("BRK.B").matches()).isTrue();
        assertThat(PublicChartController.SYMBOL.matcher("005930").matches()).isTrue();
    }

    /** 허용 IP 오류는 서버 IP 를 담고 있다. 공개 주소에서는 일반 문구만 (IP 는 데모 값) */
    @Test
    void 토스_오류_문구를_그대로_내보내지_않는다() {
        when(service.chart(anyString(), anyInt())).thenThrow(
                new AppException("토스증권 토큰 발급 실패 (HTTP 403). 지금 이 컴퓨터의 공인 IP 는 [203.0.113.7] 입니다."));

        ResponseEntity<?> res = controller.chart("MSFT");

        assertThat(res.getStatusCode().value()).isEqualTo(503);
        assertThat(String.valueOf(res.getBody())).doesNotContain("203.0.113.7").doesNotContain("IP");
    }
}
