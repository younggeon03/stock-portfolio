package com.mystock.portfolio.web;

import com.mystock.portfolio.service.tax.ForeignGainsService;
import com.mystock.portfolio.service.tax.ForeignGainsView;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 증권사를 합친 해외주식 양도소득. 내 계좌 숫자라 로그인한 나만 본다(공개 주소가 아니다).
 * 조회만 한다. 0원(증권사 조회 API 만 부른다).
 */
@Tag(name = "해외주식 양도소득", description = "토스·나무 실현손익을 합쳐 기본공제 250만 원과 예상 세액을 계산한다. 참고용")
@RestController
@RequestMapping("/api/tax")
public class ForeignGainsController {

    private final ForeignGainsService service;

    public ForeignGainsController(ForeignGainsService service) {
        this.service = service;
    }

    /**
     * @param year    비우면 올해
     * @param refresh true 면 10분 캐시를 무시하고 다시 계산한다
     */
    @GetMapping("/foreign-gains")
    public ForeignGainsView foreignGains(@RequestParam(required = false) Integer year,
                                         @RequestParam(defaultValue = "false") boolean refresh) {
        int y = year != null ? year : LocalDate.now(ZoneId.of("Asia/Seoul")).getYear();
        return service.year(y, refresh);
    }
}
