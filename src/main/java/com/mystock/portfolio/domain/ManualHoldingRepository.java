package com.mystock.portfolio.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 직접 입력한 보유종목 저장소 */
public interface ManualHoldingRepository extends JpaRepository<ManualHolding, Long> {

    /** 이 사람의 보유종목 전체 */
    List<ManualHolding> findByOwnerKey(String ownerKey);

    /** 이 사람의 특정 종목 */
    Optional<ManualHolding> findByOwnerKeyAndSymbol(String ownerKey, String symbol);

    /** 이 사람의 보유종목 전부 삭제 */
    void deleteByOwnerKey(String ownerKey);
}
