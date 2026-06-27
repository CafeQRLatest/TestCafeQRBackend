package com.restaurant.pos.inventory.repository;

import com.restaurant.pos.inventory.domain.StockLedger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface StockLedgerRepository extends JpaRepository<StockLedger, UUID> {
    
    List<StockLedger> findByWarehouseIdOrderByTransactionDateDesc(UUID warehouseId);

    Page<StockLedger> findByWarehouseIdOrderByTransactionDateDesc(UUID warehouseId, Pageable pageable);
    
    List<StockLedger> findByWarehouseIdAndProductIdOrderByTransactionDateDesc(UUID warehouseId, UUID productId);
    
    List<StockLedger> findByReferenceId(UUID referenceId);

    List<StockLedger> findByClientIdAndOrgIdAndReferenceId(UUID clientId, UUID orgId, UUID referenceId);

    List<StockLedger> findByClientIdAndOrgIdAndTransactionDateBetweenOrderByTransactionDateAsc(UUID clientId, UUID orgId, LocalDateTime from, LocalDateTime to);
}
