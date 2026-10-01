package com.mystock.portfolio.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.mystock.portfolio.domain.ManualHolding;
import com.mystock.portfolio.service.importing.ManualHoldingRequest;
import com.mystock.portfolio.service.importing.ManualHoldingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 직접 입력한 보유종목 관리.
 *
 * ★ 왜 이 기능이 있는가
 * 증권사 API 로는 키를 발급받은 본인 계좌만 조회된다. 남의 계좌를 보는 기능이 아예 없다.
 * 그래서 API 키가 없는 사람은 보유종목을 직접 알려주고, 나머지 분석은 앱이 제공한다.
 *
 * ★ 사람 구분은 X-Owner-Key 헤더로 한다
 * 로그인을 만들지 않고, 브라우저가 처음 접속할 때 만든 임의의 값을 쓴다.
 * 회원가입 없이 각자 자기 포트폴리오를 갖게 하는 가장 간단한 방법이다.
 */
@Tag(name = "직접 입력 종목", description = "X-Owner-Key 헤더로 사람을 구분한다")
@RestController
@RequestMapping("/api/manual-holdings")
public class ManualHoldingController {

    private final ManualHoldingService service;

    public ManualHoldingController(ManualHoldingService service) {
        this.service = service;
    }

    /** 내가 직접 입력한 보유종목 목록 */
    @GetMapping
    public List<Map<String, Object>> list(@RequestHeader(value = "X-Owner-Key", required = false) String ownerKey) {
        return service.list(ownerKey).stream().map(ManualHoldingController::toMap).toList();
    }

    /**
     * 보낸 목록을 기존에 더한다. 같은 종목은 덮어쓴다.
     * 스크린샷을 여러 장 올릴 때 쓴다 (국내 화면 따로, 해외 화면 따로).
     */
    @PostMapping
    public Map<String, Object> merge(@Valid @RequestBody ManualHoldingRequest request,
                                     @RequestHeader(value = "X-Owner-Key", required = false) String ownerKey) {
        int saved = service.merge(ownerKey, request.holdings());
        return Map.of("saved", saved, "message", saved + "개 종목을 저장했습니다.");
    }

    /** 보낸 목록으로 전부 교체한다. 표를 통째로 고쳤을 때 쓴다. */
    @PutMapping
    public Map<String, Object> replace(@Valid @RequestBody ManualHoldingRequest request,
                                       @RequestHeader(value = "X-Owner-Key", required = false) String ownerKey) {
        int saved = service.replaceAll(ownerKey, request.holdings());
        return Map.of("saved", saved, "message", saved + "개 종목으로 교체했습니다.");
    }

    /** 종목 하나 삭제 */
    @DeleteMapping("/{symbol}")
    public Map<String, Object> delete(@PathVariable String symbol,
                                      @RequestHeader(value = "X-Owner-Key", required = false) String ownerKey) {
        service.delete(ownerKey, symbol);
        return Map.of("message", symbol + " 을(를) 삭제했습니다.");
    }

    /** 전부 삭제 */
    @DeleteMapping
    public Map<String, Object> deleteAll(@RequestHeader(value = "X-Owner-Key", required = false) String ownerKey) {
        service.deleteAll(ownerKey);
        return Map.of("message", "직접 입력한 종목을 모두 삭제했습니다.");
    }

    /** 엔티티를 화면용 JSON 으로 */
    private static Map<String, Object> toMap(ManualHolding holding) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("symbol", holding.getSymbol());
        map.put("name", holding.getName());
        map.put("marketCountry", holding.getMarketCountry());
        map.put("currency", holding.getCurrency());
        map.put("quantity", holding.getQuantity());
        map.put("averagePurchasePrice", holding.getAveragePurchasePrice());
        map.put("updatedAt", holding.getUpdatedAt());
        return map;
    }
}
