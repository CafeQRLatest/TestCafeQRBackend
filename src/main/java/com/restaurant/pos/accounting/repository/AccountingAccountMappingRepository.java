package com.restaurant.pos.accounting.repository;

import com.restaurant.pos.accounting.domain.AccountingAccountMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountingAccountMappingRepository extends JpaRepository<AccountingAccountMapping, UUID> {

    List<AccountingAccountMapping> findByClientIdAndOrgIdAndIsactiveOrderByMappingKeyAsc(UUID clientId, UUID orgId, String isactive);

    Optional<AccountingAccountMapping> findByClientIdAndOrgIdAndMappingKeyIgnoreCase(UUID clientId, UUID orgId, String mappingKey);
}
