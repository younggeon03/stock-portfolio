package com.mystock.portfolio.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.mystock.portfolio.service.UnifiedPortfolioService;
import com.mystock.portfolio.service.UnifiedPortfolioView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 여러 증권사를 합친 포트폴리오 조회.
 *
 * 확인용 주소:
 *   /api/portfolio/unified             전체 합산 (기본)
 *   /api/portfolio/unified?scope=TOSS   토스만
 *   /api/portfolio/unified?scope=NAMUH  나무만
 *
 * ★ 경로에 /unified 가 붙은 이유
 * 예전 Yahoo 기반 화면이 쓰던 PortfolioController 가 /api/portfolio 를 이미 차지하고 있다.
 * 같은 경로를 두 컨트롤러가 쓰면 앱이 아예 안 뜨므로 경로를 나눴다.
 * 나중에 옛 코드를 정리하면 /api/portfolio 로 옮겨도 된다.
 *
 * ★★ 주문 API 는 없다. 조회와 계산만 한다. ★★
 */
@Tag(name = "합산 포트폴리오", description = "두 증권사와 직접 입력을 하나로 합친 보유종목. 화면의 표가 이걸 그린다")
@RestController
@RequestMapping("/api/portfolio")
public class UnifiedPortfolioController {

    private final UnifiedPortfolioService unifiedPortfolioService;

    public UnifiedPortfolioController(UnifiedPortfolioService unifiedPortfolioService) {
        this.unifiedPortfolioService = unifiedPortfolioService;
    }

    /**
     * @param ownerKey 브라우저가 보내는 식별자. 직접 입력한 종목을 사람별로 나누는 데 쓴다.
     *                 로그인을 만들지 않고 각자 자기 포트폴리오를 갖게 하는 가장 간단한 방법이다.
     */
    @GetMapping("/unified")
    public UnifiedPortfolioView portfolio(
            @RequestParam(required = false) String scope,
            @RequestHeader(value = "X-Owner-Key", required = false) String ownerKey) {
        return unifiedPortfolioService.load(scope, ownerKey);
    }
}
