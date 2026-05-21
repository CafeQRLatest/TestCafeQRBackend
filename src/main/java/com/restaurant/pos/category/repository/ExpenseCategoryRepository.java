package com.restaurant.pos.category.repository;

import com.restaurant.pos.category.domain.ExpenseCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, UUID> {

    List<ExpenseCategory> findByClientIdAndCreatedByOrderBySortOrderAsc(UUID clientId, String createdBy);

    @Query("""
            SELECT c FROM ExpenseCategory c
            WHERE c.clientId = :clientId
              AND ((:orgId IS NULL AND c.orgId IS NULL) OR c.orgId = :orgId)
              AND c.createdBy = :createdBy
            ORDER BY c.sortOrder ASC
            """)
    List<ExpenseCategory> findByClientIdAndOrgIdAndCreatedByOrderBySortOrderAsc(@Param("clientId") UUID clientId, @Param("orgId") UUID orgId, @Param("createdBy") String createdBy);

    @Query("""
            SELECT c FROM ExpenseCategory c
            WHERE c.id = :id
              AND c.clientId = :clientId
              AND ((:orgId IS NULL AND c.orgId IS NULL) OR c.orgId = :orgId)
              AND c.createdBy = :createdBy
            """)
    Optional<ExpenseCategory> findByIdAndClientIdAndOrgIdAndCreatedBy(@Param("id") UUID id, @Param("clientId") UUID clientId, @Param("orgId") UUID orgId, @Param("createdBy") String createdBy);

    @Query("""
            SELECT COUNT(c) > 0 FROM ExpenseCategory c
            WHERE UPPER(c.name) = UPPER(:name)
              AND c.clientId = :clientId
              AND ((:orgId IS NULL AND c.orgId IS NULL) OR c.orgId = :orgId)
              AND c.createdBy = :createdBy
            """)
    boolean existsByNameIgnoreCaseAndClientIdAndOrgIdAndCreatedBy(@Param("name") String name, @Param("clientId") UUID clientId, @Param("orgId") UUID orgId, @Param("createdBy") String createdBy);
}
