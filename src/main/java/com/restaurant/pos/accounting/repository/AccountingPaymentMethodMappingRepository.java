package com.restaurant.pos.accounting.repository;

import com.restaurant.pos.accounting.domain.AccountingPaymentMethodMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountingPaymentMethodMappingRepository extends JpaRepository<AccountingPaymentMethodMapping, UUID> {

    @Query("""
            SELECT m FROM AccountingPaymentMethodMapping m
            WHERE m.clientId = :clientId
              AND ((:orgId IS NULL AND m.orgId IS NULL) OR m.orgId = :orgId)
              AND m.isactive = :isactive
            ORDER BY m.paymentMethod ASC
            """)
    List<AccountingPaymentMethodMapping> findByClientIdAndOrgIdAndIsactiveOrderByPaymentMethodAsc(@Param("clientId") UUID clientId, @Param("orgId") UUID orgId, @Param("isactive") String isactive);

    @Query("""
            SELECT m FROM AccountingPaymentMethodMapping m
            WHERE m.clientId = :clientId
              AND ((:orgId IS NULL AND m.orgId IS NULL) OR m.orgId = :orgId)
              AND UPPER(m.paymentMethod) = UPPER(:paymentMethod)
            """)
    Optional<AccountingPaymentMethodMapping> findByClientIdAndOrgIdAndPaymentMethodIgnoreCase(@Param("clientId") UUID clientId, @Param("orgId") UUID orgId, @Param("paymentMethod") String paymentMethod);
}
