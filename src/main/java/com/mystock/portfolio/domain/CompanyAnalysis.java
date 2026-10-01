package com.mystock.portfolio.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 클로드가 만든 기업분석 결과를 저장해두는 캐시.
 *
 * ★ 왜 저장하는가
 * 분석 한 번에 몇 분이 걸리고 실제 돈이 나간다. 같은 종목을 다시 볼 때마다 새로 부르면
 * 느리고 비싸다. 그래서 한 번 만든 분석은 저장해두고 사용자가 "다시 분석" 을 누를 때만 갱신한다.
 *
 * ★ 이 테이블은 캐시이면서 동시에 작업 상태판이다
 * status 가 RUNNING 이면 지금 분석이 돌아가는 중이다. 별도의 작업 테이블을 만들지 않았다.
 *
 * ★ 성공과 실패를 분리해서 담는다
 *   analysisJson / analyzedAt : 마지막으로 **성공한** 분석
 *   status / lastError        : 마지막 **시도**의 결과
 * 재분석이 실패해도 잘 나온 이전 분석을 잃지 않기 위해서다.
 */
@Entity
@Table(name = "company_analysis")
public class CompanyAnalysis {

    /** 분석 중 */
    public static final String STATUS_RUNNING = "RUNNING";
    /** 성공 */
    public static final String STATUS_OK = "OK";
    /** 실패 */
    public static final String STATUS_ERROR = "ERROR";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 종목 심볼. 국내는 005380, 미국은 NVDA. 종목당 한 줄만 존재한다. */
    @Column(nullable = false, unique = true, length = 20)
    private String symbol;

    /** 종목명 (화면 표시용) */
    @Column(length = 100)
    private String name;

    /** 마지막 시도의 결과. RUNNING / OK / ERROR */
    @Column(nullable = false, length = 10)
    private String status;

    /**
     * 클로드가 돌려준 분석 JSON 원본. 마지막으로 성공한 것만 담는다.
     *
     * ★ LONGTEXT 를 직접 지정한 이유
     * 분석 한 건이 10~30KB 라서 기본 타입으로는 잘린다.
     * 게다가 ddl-auto=update 는 **이미 만들어진 컬럼의 타입을 바꿔주지 않으므로**
     * 처음 만들 때 제대로 잡아야 한다.
     */
    @Lob
    @Column(name = "analysis_json", columnDefinition = "LONGTEXT")
    private String analysisJson;

    /** 마지막으로 성공한 시각 */
    @Column(name = "analyzed_at")
    private LocalDateTime analyzedAt;

    /** 마지막으로 시도한 시각 (실패 포함) */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** 마지막 시도가 실패했을 때의 이유. 성공하면 null */
    @Column(name = "last_error", length = 500)
    private String lastError;

    /** 어떤 모델로 만들었는지 */
    @Column(length = 50)
    private String model;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "web_search_count")
    private Integer webSearchCount;

    protected CompanyAnalysis() {
        // JPA 기본 생성자
    }

    public CompanyAnalysis(String symbol, String name) {
        this.symbol = symbol;
        this.name = name;
        this.status = STATUS_RUNNING;
        this.updatedAt = LocalDateTime.now();
    }

    /** 분석을 시작했다고 표시한다 */
    public void markRunning(String name) {
        this.name = name;
        this.status = STATUS_RUNNING;
        this.lastError = null;
        this.updatedAt = LocalDateTime.now();
    }

    /** 분석에 성공했다. 결과를 갈아끼운다. */
    public void markSuccess(String analysisJson, String model,
                            Integer inputTokens, Integer outputTokens, Integer webSearchCount) {
        this.analysisJson = analysisJson;
        this.model = model;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.webSearchCount = webSearchCount;
        this.status = STATUS_OK;
        this.lastError = null;
        this.analyzedAt = LocalDateTime.now();
        this.updatedAt = this.analyzedAt;
    }

    /**
     * 분석에 실패했다.
     * ★ analysisJson 과 analyzedAt 은 건드리지 않는다. 잘 나온 이전 분석을 잃으면 안 된다.
     */
    public void markFailed(String reason) {
        this.status = STATUS_ERROR;
        this.lastError = trim(reason);
        this.updatedAt = LocalDateTime.now();
    }

    /** 성공한 분석을 갖고 있는지 */
    public boolean hasAnalysis() {
        return analysisJson != null && !analysisJson.isBlank();
    }

    /** 컬럼 길이를 넘지 않게 자른다 */
    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 500 ? value : value.substring(0, 497) + "...";
    }

    public Long getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getName() {
        return name;
    }

    public String getStatus() {
        return status;
    }

    public String getAnalysisJson() {
        return analysisJson;
    }

    public LocalDateTime getAnalyzedAt() {
        return analyzedAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public String getLastError() {
        return lastError;
    }

    public String getModel() {
        return model;
    }

    public Integer getInputTokens() {
        return inputTokens;
    }

    public Integer getOutputTokens() {
        return outputTokens;
    }

    public Integer getWebSearchCount() {
        return webSearchCount;
    }
}
