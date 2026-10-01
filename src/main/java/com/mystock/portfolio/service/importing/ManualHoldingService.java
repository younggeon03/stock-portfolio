package com.mystock.portfolio.service.importing;

import com.mystock.portfolio.domain.ManualHolding;
import com.mystock.portfolio.domain.ManualHoldingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 직접 입력한 보유종목을 관리한다.
 *
 * 스크린샷으로 읽었든 손으로 입력했든 결국 여기로 들어와 저장된다.
 */
@Service
public class ManualHoldingService {

    private static final Logger log = LoggerFactory.getLogger(ManualHoldingService.class);

    private final ManualHoldingRepository repository;

    public ManualHoldingService(ManualHoldingRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ManualHolding> list(String ownerKey) {
        requireOwner(ownerKey);
        return repository.findByOwnerKey(ownerKey);
    }

    /**
     * 보낸 목록을 기존에 더한다. 같은 종목이 있으면 덮어쓴다.
     *
     * 스크린샷을 여러 장 올릴 때 쓴다.
     * (국내 화면 한 장, 해외 화면 한 장을 따로 캡처하는 경우가 많다)
     */
    @Transactional
    public int merge(String ownerKey, List<ManualHoldingRequest.Row> rows) {
        requireOwner(ownerKey);

        int saved = 0;
        for (ManualHoldingRequest.Row row : rows) {
            String symbol = row.symbol().trim().toUpperCase();

            repository.findByOwnerKeyAndSymbol(ownerKey, symbol)
                    .ifPresentOrElse(
                            existing -> existing.update(row.name(), row.marketCountry(), row.currency(),
                                    row.quantity(), row.averagePurchasePrice()),
                            () -> repository.save(new ManualHolding(ownerKey, symbol, row.name(),
                                    row.marketCountry(), row.currency(),
                                    row.quantity(), row.averagePurchasePrice())));
            saved++;
        }
        log.info("직접 입력 보유종목 저장: {}건", saved);
        return saved;
    }

    /**
     * 보낸 목록으로 전부 교체한다.
     * 화면에서 표를 통째로 고쳤을 때 쓴다.
     */
    @Transactional
    public int replaceAll(String ownerKey, List<ManualHoldingRequest.Row> rows) {
        requireOwner(ownerKey);
        repository.deleteByOwnerKey(ownerKey);
        // 지운 것이 확실히 반영된 뒤에 새로 넣는다
        repository.flush();
        return merge(ownerKey, rows);
    }

    /** 종목 하나 삭제 */
    @Transactional
    public void delete(String ownerKey, String symbol) {
        requireOwner(ownerKey);
        repository.findByOwnerKeyAndSymbol(ownerKey, symbol.trim().toUpperCase())
                .ifPresent(repository::delete);
    }

    /** 전부 삭제 */
    @Transactional
    public void deleteAll(String ownerKey) {
        requireOwner(ownerKey);
        repository.deleteByOwnerKey(ownerKey);
    }

    private void requireOwner(String ownerKey) {
        if (ownerKey == null || ownerKey.isBlank()) {
            throw new IllegalArgumentException(
                    "사용자 식별값(X-Owner-Key)이 없습니다. 브라우저를 새로고침해 주세요.");
        }
    }
}
