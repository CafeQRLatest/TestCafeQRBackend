package com.restaurant.pos.product.repository;

import com.restaurant.pos.product.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {
    @Query("SELECT c FROM Category c WHERE c.clientId = :clientId AND c.orgId = :orgId")
    List<Category> findByClientIdAndOrgIdOrGlobal(UUID clientId, UUID orgId);

    @Query("SELECT c FROM Category c WHERE c.clientId = :clientId AND c.orgId = :orgId AND c.isActive = true")
    List<Category> findByClientIdAndOrgIdOrGlobalAndIsActiveTrue(UUID clientId, UUID orgId);

    @Query("SELECT c FROM Category c WHERE c.clientId = :clientId AND c.orgId = :orgId AND c.updatedAt >= :updatedAfter")
    List<Category> findChangedByClientIdAndOrgIdOrGlobal(UUID clientId, UUID orgId, LocalDateTime updatedAfter);
    
    @Query("SELECT c FROM Category c WHERE c.id = :id AND c.clientId = :clientId AND c.orgId = :orgId")
    Optional<Category> findByIdAndClientIdAndOrgIdOrGlobal(UUID id, UUID clientId, UUID orgId);

    @Query("SELECT c FROM Category c WHERE c.name = :name AND c.clientId = :clientId AND c.orgId = :orgId")
    Optional<Category> findByNameAndClientIdAndOrgIdOrGlobal(String name, UUID clientId, UUID orgId);
}
