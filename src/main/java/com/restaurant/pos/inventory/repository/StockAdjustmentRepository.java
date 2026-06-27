package com.restaurant.pos.inventory.repository;

import com.restaurant.pos.inventory.domain.StockAdjustment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockAdjustmentRepository extends JpaRepository<StockAdjustment, UUID> {
    
    List<StockAdjustment> findByClientIdOrderByAdjustmentDateDesc(UUID clientId);

    Page<StockAdjustment> findByClientIdOrderByAdjustmentDateDesc(UUID clientId, Pageable pageable);

    List<StockAdjustment> findByClientIdAndOrgIdOrderByAdjustmentDateDesc(UUID clientId, UUID orgId);

    Page<StockAdjustment> findByClientIdAndOrgIdOrderByAdjustmentDateDesc(UUID clientId, UUID orgId, Pageable pageable);

    @Query("""
            SELECT a FROM StockAdjustment a
            WHERE a.clientId = :clientId
              AND (:orgId IS NULL OR a.orgId = :orgId)
              AND a.adjustmentDate BETWEEN :from AND :to
            ORDER BY a.adjustmentDate ASC
            """)
    List<StockAdjustment> findByClientIdAndOrgIdAndAdjustmentDateBetweenOrderByAdjustmentDateAsc(@Param("clientId") UUID clientId, @Param("orgId") UUID orgId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
    
    Optional<StockAdjustment> findByIdAndClientId(UUID id, UUID clientId);
    
    Optional<StockAdjustment> findByIdAndClientIdAndOrgId(UUID id, UUID clientId, UUID orgId);
}
