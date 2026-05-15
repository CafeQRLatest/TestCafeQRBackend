package com.restaurant.pos.accounting.repository;

import com.restaurant.pos.accounting.domain.AccountingAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountingAccountRepository extends JpaRepository<AccountingAccount, UUID>, JpaSpecificationExecutor<AccountingAccount> {

    Optional<AccountingAccount> findByIdAndClientId(UUID id, UUID clientId);

    Optional<AccountingAccount> findByIdAndClientIdAndOrgId(UUID id, UUID clientId, UUID orgId);

    List<AccountingAccount> findByClientIdOrderByCodeAsc(UUID clientId);

    List<AccountingAccount> findByClientIdAndOrgIdOrderByCodeAsc(UUID clientId, UUID orgId);

    boolean existsByClientIdAndOrgIdAndCodeIgnoreCase(UUID clientId, UUID orgId, String code);

    boolean existsByClientIdAndOrgIdAndCodeIgnoreCaseAndIdNot(UUID clientId, UUID orgId, String code, UUID id);
}
