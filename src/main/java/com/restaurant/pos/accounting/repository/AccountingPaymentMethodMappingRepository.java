package com.restaurant.pos.accounting.repository;

import com.restaurant.pos.accounting.domain.AccountingPaymentMethodMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountingPaymentMethodMappingRepository extends JpaRepository<AccountingPaymentMethodMapping, UUID> {

    List<AccountingPaymentMethodMapping> findByClientIdAndOrgIdAndIsactiveOrderByPaymentMethodAsc(UUID clientId, UUID orgId, String isactive);

    Optional<AccountingPaymentMethodMapping> findByClientIdAndOrgIdAndPaymentMethodIgnoreCase(UUID clientId, UUID orgId, String paymentMethod);
}
