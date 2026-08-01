package com.restaurant.pos.inventory.repository;

import com.restaurant.pos.inventory.domain.StockTransfer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockTransferRepository extends JpaRepository<StockTransfer, UUID> {
    
    List<StockTransfer> findByClientIdOrderByTransferDateDesc(UUID clientId);

    Page<StockTransfer> findByClientIdOrderByTransferDateDesc(UUID clientId, Pageable pageable);

    List<StockTransfer> findByClientIdAndOrgIdOrderByTransferDateDesc(UUID clientId, UUID orgId);

    Page<StockTransfer> findByClientIdAndOrgIdOrderByTransferDateDesc(UUID clientId, UUID orgId, Pageable pageable);

    @Query("SELECT t FROM StockTransfer t WHERE t.clientId = :clientId AND (t.orgId = :orgId OR t.destWarehouseId IN (SELECT w.id FROM Warehouse w WHERE w.orgId = :orgId)) ORDER BY t.transferDate DESC")
    Page<StockTransfer> findByClientIdAndOrgIdOrDestOrgId(@Param("clientId") UUID clientId, @Param("orgId") UUID orgId, Pageable pageable);
    
    Optional<StockTransfer> findByIdAndClientId(UUID id, UUID clientId);
    
    Optional<StockTransfer> findByIdAndClientIdAndOrgId(UUID id, UUID clientId, UUID orgId);
}
