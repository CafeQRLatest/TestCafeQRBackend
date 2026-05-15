package com.restaurant.pos.product.repository;

import com.restaurant.pos.product.domain.Uom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface UomRepository extends JpaRepository<Uom, UUID> {
    @Query("SELECT u FROM Uom u WHERE u.clientId = :clientId AND (:orgId IS NULL OR u.orgId IS NULL OR u.orgId = :orgId)")
    List<Uom> findByClientIdAndOrgIdOrGlobal(UUID clientId, UUID orgId);

    @Query("SELECT u FROM Uom u WHERE u.clientId = :clientId AND (:orgId IS NULL OR u.orgId IS NULL OR u.orgId = :orgId) AND u.isActive = true")
    List<Uom> findByClientIdAndOrgIdOrGlobalAndIsActiveTrue(UUID clientId, UUID orgId);

    @Query("SELECT u FROM Uom u WHERE u.clientId = :clientId AND (:orgId IS NULL OR u.orgId IS NULL OR u.orgId = :orgId) AND u.updatedAt >= :updatedAfter")
    List<Uom> findChangedByClientIdAndOrgIdOrGlobal(UUID clientId, UUID orgId, LocalDateTime updatedAfter);

    @Query("SELECT u FROM Uom u WHERE u.name = :name AND u.clientId = :clientId AND (:orgId IS NULL OR u.orgId IS NULL OR u.orgId = :orgId)")
    java.util.Optional<Uom> findByNameAndClientIdAndOrgIdOrGlobal(String name, UUID clientId, UUID orgId);
}
