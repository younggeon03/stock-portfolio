package com.mystock.portfolio.service.analysis;

import com.mystock.portfolio.domain.CompanyAnalysis;
import com.mystock.portfolio.domain.CompanyAnalysisRepository;
import com.mystock.portfolio.external.anthropic.ClaudeCallResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 기업분석 캐시를 읽고 쓰는 부분만 따로 떼어낸 클래스.
 *
 * ★ 왜 굳이 나눴는가
 * 분석은 몇 분이 걸려서 별도 스레드에서 돌린다.
 * 그런데 **요청 스레드의 트랜잭션은 다른 스레드로 따라가지 않는다.**
 * 그래서 백그라운드 작업이 결과를 저장할 때 쓸 @Transactional 진입점이 따로 필요하다.
 *
 * 같은 클래스 안에서 자기 메서드를 호출하면 스프링의 프록시를 거치지 않아
 * @Transactional 이 동작하지 않는다. 별도 빈으로 두면 그 함정도 피한다.
 */
@Component
public class CompanyAnalysisStore {

    private final CompanyAnalysisRepository repository;

    public CompanyAnalysisStore(CompanyAnalysisRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Optional<CompanyAnalysis> find(String symbol) {
        return repository.findBySymbol(symbol);
    }

    @Transactional(readOnly = true)
    public List<CompanyAnalysis> findAll(Collection<String> symbols) {
        return repository.findBySymbolIn(symbols);
    }

    /** 분석을 시작한다고 표시한다. 없으면 새로 만든다. */
    @Transactional
    public void beginRun(String symbol, String name) {
        CompanyAnalysis entity = repository.findBySymbol(symbol)
                .orElseGet(() -> repository.save(new CompanyAnalysis(symbol, name)));
        entity.markRunning(name);
        repository.save(entity);
    }

    /** 성공 결과를 저장한다 */
    @Transactional
    public void saveSuccess(String symbol, ClaudeCallResult result, boolean includesPosition) {
        repository.findBySymbol(symbol).ifPresent(entity -> {
            // 캐시를 켠 뒤로 inputTokens 는 "캐시에 없던 나머지" 라서 실제 프롬프트 크기보다 작다.
            // 기록은 캐싱 전후를 같은 눈금으로 비교할 수 있어야 하므로 전체 크기를 남긴다.
            entity.markSuccess(result.analysisJson(), result.model(),
                    result.totalPromptTokens(), result.outputTokens(), result.webSearchCount(), includesPosition);
            repository.save(entity);
        });
    }

    /** 판단만 새로 쓴 결과를 저장한다. 조사 시각은 엔티티가 그대로 둔다 */
    @Transactional
    public void saveReassessment(String symbol, ClaudeCallResult result, boolean includesPosition) {
        repository.findBySymbol(symbol).ifPresent(entity -> {
            entity.markReassessed(result.analysisJson(), result.model(),
                    result.totalPromptTokens(), result.outputTokens(), includesPosition);
            repository.save(entity);
        });
    }

    /**
     * 실패를 기록한다.
     * ★ 이전에 성공한 분석은 그대로 둔다. 재분석 실패로 멀쩡한 분석을 잃으면 안 된다.
     */
    @Transactional
    public void saveFailure(String symbol, String reason) {
        repository.findBySymbol(symbol).ifPresent(entity -> {
            entity.markFailed(reason);
            repository.save(entity);
        });
    }
}
