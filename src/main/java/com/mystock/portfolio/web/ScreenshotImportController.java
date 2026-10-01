package com.mystock.portfolio.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.mystock.portfolio.service.importing.ImportPreview;
import com.mystock.portfolio.service.importing.ImportedHolding;
import com.mystock.portfolio.service.importing.ScreenshotImportService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

/**
 * 증권사 앱 스크린샷에서 보유종목을 읽어온다.
 *
 * 흐름:
 *   1. 사용자가 잔고 화면을 캡처해서 올린다
 *   2. 클로드가 이미지를 읽어 종목명·수량·평단가를 뽑는다
 *   3. 종목명을 실제 코드로 바꾸고 숫자를 다듬는다
 *   4. 화면에서 사람이 확인·수정한다
 *   5. /api/manual-holdings 로 저장한다
 *
 * ★ 여기서는 저장하지 않는다. 읽어서 돌려주기만 한다.
 *   글자를 잘못 읽을 수 있으므로 반드시 사람 확인을 거친다.
 */
@Tag(name = "스크린샷 등록", description = "증권사 앱 캡처에서 종목·수량·평단가를 읽는다")
@RestController
@RequestMapping("/api/import")
public class ScreenshotImportController {

    /** 받아들일 이미지 형식 */
    private static final List<String> ALLOWED_TYPES =
            List.of("image/png", "image/jpeg", "image/jpg", "image/gif", "image/webp");

    private final ScreenshotImportService importService;

    public ScreenshotImportController(ScreenshotImportService importService) {
        this.importService = importService;
    }

    /**
     * 스크린샷을 분석한다.
     *
     * 이 호출은 Claude 를 쓰므로 비용이 든다. 한 장에 대략 수십 원 수준이다.
     * (기업분석보다는 훨씬 싸다. 웹검색을 하지 않고 이미지 한 장만 읽기 때문이다)
     */
    @PostMapping("/screenshot")
    public ImportPreview screenshot(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("이미지 파일이 필요합니다.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException(
                    "이미지 파일만 올릴 수 있습니다 (PNG, JPG, GIF, WEBP). 받은 형식: " + contentType);
        }

        return importService.preview(file.getBytes(), contentType);
    }

    /**
     * 샘플 응답. Claude 를 부르지 않으므로 **비용이 0원**이다.
     *
     * ★ 왜 필요한가
     * 스크린샷 분석은 호출할 때마다 돈이 든다. 화면을 만들고 고치는 동안 매번 실제로 부르면 낭비다.
     * 이 주소는 실제와 똑같은 모양의 가짜 결과를 돌려주므로, 검수 화면이 잘 그려지는지
     * 돈 한 푼 안 쓰고 확인할 수 있다.
     *
     * 일부러 까다로운 경우를 섞어뒀다.
     *   - 정상적으로 잘 읽힌 줄
     *   - 종목을 못 찾은 줄
     *   - 평단가를 못 읽은 줄
     *   - 비슷한 종목을 찾았지만 확신이 낮은 줄
     */
    @PostMapping("/screenshot/sample")
    public ImportPreview sample() {
        List<ImportedHolding> rows = List.of(
                // 1) 모든 게 잘 읽힌 경우
                new ImportedHolding("삼성전자", "005930", "삼성전자", "KR", "KRW",
                        new BigDecimal("30"), new BigDecimal("71500"),
                        100, 98, false, List.of()),

                // 2) 소수점 매수 + 미국 종목
                new ImportedHolding("엔비디아", "NVDA", "엔비디아", "US", "USD",
                        new BigDecimal("0.034888"), new BigDecimal("136.93"),
                        100, 95, false, List.of()),

                // 3) 평단가를 못 읽음 → 사용자가 채워야 함
                new ImportedHolding("현대차", "005380", "현대차", "KR", "KRW",
                        new BigDecimal("5"), null,
                        100, 88, true, List.of("평단가를 읽지 못했습니다")),

                // 4) 비슷한 종목을 찾았지만 확신이 낮음
                new ImportedHolding("대한전선우", "001445", "대한전선우", "KR", "KRW",
                        new BigDecimal("12"), new BigDecimal("18300"),
                        62, 74, true, List.of("비슷한 종목을 찾았습니다. 맞는지 확인해주세요")),

                // 5) 종목을 아예 못 찾음 → 사용자가 직접 골라야 함
                new ImportedHolding("알 수 없는 종목", null, "알 수 없는 종목", null, null,
                        new BigDecimal("100"), new BigDecimal("5000"),
                        0, 45, true,
                        List.of("종목을 찾지 못했습니다. 직접 골라주세요", "글자가 또렷하지 않았습니다", "통화를 정하지 못했습니다"))
        );

        int needsReview = (int) rows.stream().filter(ImportedHolding::needsReview).count();

        return new ImportPreview(rows,
                "샘플 응답입니다. 실제 Claude 호출 없이 만들어진 값이라 비용이 들지 않습니다.",
                needsReview, false, ImportPreview.GUIDE);
    }
}
