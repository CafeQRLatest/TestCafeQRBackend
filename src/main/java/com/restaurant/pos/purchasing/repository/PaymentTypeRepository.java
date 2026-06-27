package com.restaurant.pos.purchasing.repository;

import com.restaurant.pos.purchasing.domain.PaymentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentTypeRepository extends JpaRepository<PaymentType, UUID> {

    List<PaymentType> findByClientIdAndOrgIdOrderBySortOrderAscDisplayNameAsc(UUID clientId, UUID orgId);

    List<PaymentType> findByClientIdOrderBySortOrderAscDisplayNameAsc(UUID clientId);

    Optional<PaymentType> findByIdAndClientId(UUID id, UUID clientId);

    boolean existsByClientIdAndOrgIdAndDisplayName(UUID clientId, UUID orgId, String displayName);

    List<PaymentType> findByClientIdAndOrgIdAndSalesOrderBySortOrderAsc(UUID clientId, UUID orgId, String sales);

    List<PaymentType> findByClientIdAndOrgIdAndPurchaseOrderBySortOrderAsc(UUID clientId, UUID orgId, String purchase);

    List<PaymentType> findByClientIdAndOrgIdAndExpenseOrderBySortOrderAsc(UUID clientId, UUID orgId, String expense);

    List<PaymentType> findByClientIdAndIsDefaultTrueAndOrgId(UUID clientId, UUID orgId);

    boolean existsByClientIdAndOrgIdAndSortOrder(UUID clientId, UUID orgId, Integer sortOrder);

    boolean existsByClientIdAndOrgIdAndSortOrderAndIdNot(UUID clientId, UUID orgId, Integer sortOrder, UUID id);
}
