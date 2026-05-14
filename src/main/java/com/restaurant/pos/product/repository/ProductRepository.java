package com.restaurant.pos.product.repository;

import com.restaurant.pos.product.domain.Product;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {
    List<Product> findByClientId(UUID clientId);

    @EntityGraph(attributePaths = {"category", "uom"})
    @Query("SELECT p FROM Product p WHERE p.clientId = :clientId AND (:orgId IS NULL OR p.orgId IS NULL OR p.orgId = :orgId)")
    List<Product> findByClientIdAndOrgIdOrGlobal(UUID clientId, UUID orgId);

    @EntityGraph(attributePaths = {"category", "uom"})
    @Query("SELECT p FROM Product p WHERE p.clientId = :clientId AND (:orgId IS NULL OR p.orgId IS NULL OR p.orgId = :orgId) AND p.isActive = true")
    List<Product> findByClientIdAndOrgIdOrGlobalAndIsActiveTrue(UUID clientId, UUID orgId);

    @EntityGraph(attributePaths = {"category", "uom"})
    List<Product> findByIdIn(List<UUID> ids);

    @Query("SELECT COUNT(p) > 0 FROM Product p WHERE p.productCode = :code AND p.clientId = :clientId AND (:orgId IS NULL OR p.orgId IS NULL OR p.orgId = :orgId) AND p.isActive = true")
    boolean existsByProductCodeAndClientIdAndOrgIdOrGlobal(String code, UUID clientId, UUID orgId);

    boolean existsByVariantMappings_VariantGroup_IdAndIsActiveTrue(UUID variantGroupId);

    boolean existsByVariantPricings_VariantOption_IdAndIsActiveTrue(UUID variantOptionId);
}
