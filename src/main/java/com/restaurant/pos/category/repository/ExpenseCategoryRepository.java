package com.restaurant.pos.category.repository;

import com.restaurant.pos.category.domain.ExpenseCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, UUID> {

    List<ExpenseCategory> findByClientIdAndOrgIdAndCreatedByOrderBySortOrderAsc(UUID clientId, UUID orgId, String createdBy);

    Optional<ExpenseCategory> findByIdAndClientIdAndOrgIdAndCreatedBy(UUID id, UUID clientId, UUID orgId, String createdBy);

    boolean existsByNameIgnoreCaseAndClientIdAndOrgIdAndCreatedBy(String name, UUID clientId, UUID orgId, String createdBy);
}
