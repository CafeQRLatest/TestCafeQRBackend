package com.restaurant.pos.product.repository;

import com.restaurant.pos.product.domain.Product;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {
    List<Product> findByClientId(UUID clientId);

    @EntityGraph(attributePaths = {"category"})
    Optional<Product> findWithCategoryById(UUID id);

    @EntityGraph(attributePaths = {"category", "uom", "defaultPricelist"})
    @Query("SELECT p FROM Product p WHERE p.clientId = :clientId AND (:orgId IS NULL OR p.orgId IS NULL OR p.orgId = :orgId)")
    List<Product> findByClientIdAndOrgIdOrGlobal(UUID clientId, UUID orgId);

    @EntityGraph(attributePaths = {"category", "uom", "defaultPricelist"})
    @Query("SELECT p FROM Product p WHERE p.clientId = :clientId AND (:orgId IS NULL OR p.orgId IS NULL OR p.orgId = :orgId) AND p.isActive = true")
    List<Product> findByClientIdAndOrgIdOrGlobalAndIsActiveTrue(UUID clientId, UUID orgId);

    @EntityGraph(attributePaths = {"category", "uom"})
    @Query("SELECT p FROM Product p WHERE p.clientId = :clientId AND (:orgId IS NULL OR p.orgId IS NULL OR p.orgId = :orgId) AND p.updatedAt >= :updatedAfter")
    List<Product> findChangedByClientIdAndOrgIdOrGlobal(UUID clientId, UUID orgId, LocalDateTime updatedAfter);

    @EntityGraph(attributePaths = {"category", "uom"})
    List<Product> findByIdIn(List<UUID> ids);

    @Query("SELECT COUNT(p) > 0 FROM Product p WHERE p.productCode = :code AND p.clientId = :clientId AND (:orgId IS NULL OR p.orgId IS NULL OR p.orgId = :orgId) AND p.isActive = true")
    boolean existsByProductCodeAndClientIdAndOrgIdOrGlobal(String code, UUID clientId, UUID orgId);

    @Query("SELECT COUNT(p) > 0 FROM Product p WHERE LOWER(p.name) = LOWER(:name) AND p.clientId = :clientId AND (:orgId IS NULL OR p.orgId IS NULL OR p.orgId = :orgId) AND p.isActive = true AND (:id IS NULL OR p.id != :id)")
    boolean existsByNameAndClientIdAndOrgIdOrGlobalAndIdNot(String name, UUID clientId, UUID orgId, UUID id);

    @Query("SELECT COUNT(p) > 0 FROM Product p WHERE LOWER(p.productCode) = LOWER(:code) AND p.clientId = :clientId AND (:orgId IS NULL OR p.orgId IS NULL OR p.orgId = :orgId) AND p.isActive = true AND (:id IS NULL OR p.id != :id)")
    boolean existsByProductCodeAndClientIdAndOrgIdOrGlobalAndIdNot(String code, UUID clientId, UUID orgId, UUID id);

    boolean existsByVariantMappings_VariantGroup_IdAndIsActiveTrue(UUID variantGroupId);

    boolean existsByVariantPricings_VariantOption_IdAndIsActiveTrue(UUID variantOptionId);
}
