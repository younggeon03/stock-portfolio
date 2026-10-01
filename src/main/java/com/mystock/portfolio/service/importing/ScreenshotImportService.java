package com.mystock.portfolio.service.importing;

import com.mystock.portfolio.external.anthropic.ScreenshotParseResult;
import com.mystock.portfolio.external.anthropic.ScreenshotParser;
import com.mystock.portfolio.service.symbol.SymbolMasterService;
import com.mystock.portfolio.service.symbol.SymbolMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 스크린샷을 읽어 화면에 보여줄 표로 바꾼다.
 *
 * 하는 일 세 가지
 *   1. 클로드에게 이미지를 보내 글자를 읽는다
 *   2. 읽은 이름("현대차")을 실제 종목코드(005380)로 바꾼다
 *   3. 숫자에 섞인 콤마·'주'·'원'·'$' 를 떼어내고 숫자로 만든다
 *
 * ★ 저장은 하지 않는다
 * 결과를 돌려주기만 하고, 사람이 화면에서 확인·수정한 뒤에 따로 저장한다.
 * 글자를 잘못 읽었을 수 있기 때문이다.
 */
@Service
public class ScreenshotImportService {

    private static final Logger log = LoggerFactory.getLogger(ScreenshotImportService.class);

    /** 숫자에서 떼어낼 글자들. 콤마, 주, 원, 달러기호, 공백, 원화기호 */
    private static final String JUNK_PATTERN = "[,\\s주원$₩%]";

    private final ScreenshotParser parser;
    private final SymbolMasterService symbolMasterService;

    public ScreenshotImportService(ScreenshotParser parser, SymbolMasterService symbolMasterService) {
        this.parser = parser;
        this.symbolMasterService = symbolMasterService;
    }

    /** 스크린샷을 읽어 검수용 표를 만든다 */
    public ImportPreview preview(byte[] imageBytes, String contentType) {
        ScreenshotParseResult parsed = parser.parse(imageBytes, contentType);

        boolean masterEmpty = symbolMasterService.size() == 0;
        if (masterEmpty) {
            log.warn("종목 마스터가 비어 있어 종목코드를 찾을 수 없습니다. /api/symbols/refresh 로 먼저 받아오세요.");
        }

        List<ImportedHolding> rows = new ArrayList<>();
        int needsReview = 0;

        for (ScreenshotParseResult.Row row : safeRows(parsed)) {
            ImportedHolding holding = toHolding(row, masterEmpty);
            if (holding.needsReview()) {
                needsReview++;
            }
            rows.add(holding);
        }

        return new ImportPreview(rows, parsed.note() == null ? "" : parsed.note(),
                needsReview, masterEmpty, ImportPreview.GUIDE);
    }

    private List<ScreenshotParseResult.Row> safeRows(ScreenshotParseResult parsed) {
        return parsed.holdings() == null ? List.of() : parsed.holdings();
    }

    /** 읽은 한 줄을 다듬는다 */
    private ImportedHolding toHolding(ScreenshotParseResult.Row row, boolean masterEmpty) {
        List<String> issues = new ArrayList<>();

        // 1. 이름 → 종목코드
        SymbolMatch match = masterEmpty ? SymbolMatch.notFound() : symbolMasterService.findByName(row.name());
        if (!match.found()) {
            issues.add(masterEmpty
                    ? "종목 목록을 아직 받아오지 않아 코드를 찾을 수 없습니다"
                    : "종목을 찾지 못했습니다. 직접 골라주세요");
        } else if (match.confidence() < SymbolMatch.NEEDS_REVIEW_BELOW) {
            issues.add("비슷한 종목을 찾았습니다. 맞는지 확인해주세요");
        }

        // 2. 숫자 다듬기
        BigDecimal quantity = toNumber(row.quantity());
        if (quantity == null) {
            issues.add("수량을 읽지 못했습니다");
        } else if (quantity.signum() <= 0) {
            issues.add("수량이 0 이하입니다");
            quantity = null;
        }

        BigDecimal averagePrice = toNumber(row.averagePrice());
        if (averagePrice == null) {
            issues.add("평단가를 읽지 못했습니다");
        } else if (averagePrice.signum() <= 0) {
            issues.add("평단가가 0 이하입니다");
            averagePrice = null;
        }

        // 3. 통화 정하기. 읽어낸 값이 없으면 종목 정보에서 가져온다
        String currency = normalizeCurrency(row.currency());
        if (currency == null) {
            currency = match.currency();
        }
        if (currency == null) {
            issues.add("통화를 정하지 못했습니다");
        }

        // 4. 글자를 흐릿하게 읽었으면 확인 대상
        if (row.confidence() < 70) {
            issues.add("글자가 또렷하지 않았습니다");
        }

        boolean needsReview = !issues.isEmpty();

        return new ImportedHolding(
                row.name(),
                match.symbol(),
                match.found() ? match.name() : row.name(),
                match.marketCountry(),
                currency,
                quantity,
                averagePrice,
                match.confidence(),
                row.confidence(),
                needsReview,
                issues);
    }

    /**
     * "1,234주" → 1234, "412,000원" → 412000, "$151.82" → 151.82
     *
     * 클로드에게 이미 숫자만 남기라고 시켰지만, 콤마나 단위가 남아올 수 있으므로
     * 여기서 한 번 더 털어낸다. 소수점은 반드시 살린다(소수점 매수가 흔하다).
     */
    private BigDecimal toNumber(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = raw.replaceAll(JUNK_PATTERN, "");
        if (cleaned.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            log.debug("숫자로 바꾸지 못했습니다: {}", raw);
            return null;
        }
    }

    /** KRW / USD 만 인정한다. UNKNOWN 이나 이상한 값은 null */
    private String normalizeCurrency(String raw) {
        if (raw == null) {
            return null;
        }
        String upper = raw.trim().toUpperCase();
        return ("KRW".equals(upper) || "USD".equals(upper)) ? upper : null;
    }
}
