package com.restaurant.pos.product.repository;

import com.restaurant.pos.product.domain.VariantGroup;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface VariantGroupRepository extends JpaRepository<VariantGroup, UUID> {
    @EntityGraph(attributePaths = {"options"})
    @Query("SELECT DISTINCT v FROM VariantGroup v WHERE v.clientId = :clientId AND (:orgId IS NULL OR v.orgId IS NULL OR v.orgId = :orgId)")
    List<VariantGroup> findByClientIdAndOrgIdOrGlobal(UUID clientId, UUID orgId);

    @EntityGraph(attributePaths = {"options"})
    @Query("SELECT DISTINCT v FROM VariantGroup v WHERE v.clientId = :clientId AND (:orgId IS NULL OR v.orgId IS NULL OR v.orgId = :orgId) AND v.isActive = true")
    List<VariantGroup> findByClientIdAndOrgIdOrGlobalAndIsActiveTrue(UUID clientId, UUID orgId);

    @EntityGraph(attributePaths = {"options"})
    @Query("SELECT DISTINCT v FROM VariantGroup v WHERE v.clientId = :clientId AND (:orgId IS NULL OR v.orgId IS NULL OR v.orgId = :orgId) AND v.updatedAt >= :updatedAfter")
    List<VariantGroup> findChangedByClientIdAndOrgIdOrGlobal(UUID clientId, UUID orgId, LocalDateTime updatedAfter);
}
