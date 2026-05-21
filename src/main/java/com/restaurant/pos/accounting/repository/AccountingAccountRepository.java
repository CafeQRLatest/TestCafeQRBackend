package com.restaurant.pos.accounting.repository;

import com.restaurant.pos.accounting.domain.AccountingAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountingAccountRepository extends JpaRepository<AccountingAccount, UUID>, JpaSpecificationExecutor<AccountingAccount> {

    Optional<AccountingAccount> findByIdAndClientId(UUID id, UUID clientId);

    @Query("""
            SELECT a FROM AccountingAccount a
            WHERE a.id = :id
              AND a.clientId = :clientId
              AND ((:orgId IS NULL AND a.orgId IS NULL) OR a.orgId = :orgId)
            """)
    Optional<AccountingAccount> findByIdAndClientIdAndOrgId(@Param("id") UUID id, @Param("clientId") UUID clientId, @Param("orgId") UUID orgId);

    List<AccountingAccount> findByClientIdOrderByCodeAsc(UUID clientId);

    @Query("""
            SELECT a FROM AccountingAccount a
            WHERE a.clientId = :clientId
              AND ((:orgId IS NULL AND a.orgId IS NULL) OR a.orgId = :orgId)
            ORDER BY a.code ASC
            """)
    List<AccountingAccount> findByClientIdAndOrgIdOrderByCodeAsc(@Param("clientId") UUID clientId, @Param("orgId") UUID orgId);

    @Query("""
            SELECT a FROM AccountingAccount a
            WHERE a.clientId = :clientId
              AND ((:orgId IS NULL AND a.orgId IS NULL) OR a.orgId = :orgId)
              AND UPPER(a.systemKey) = UPPER(:systemKey)
            """)
    Optional<AccountingAccount> findByClientIdAndOrgIdAndSystemKeyIgnoreCase(@Param("clientId") UUID clientId, @Param("orgId") UUID orgId, @Param("systemKey") String systemKey);

    @Query("""
            SELECT a FROM AccountingAccount a
            WHERE a.clientId = :clientId
              AND ((:orgId IS NULL AND a.orgId IS NULL) OR a.orgId = :orgId)
              AND UPPER(a.code) = UPPER(:code)
            """)
    Optional<AccountingAccount> findByClientIdAndOrgIdAndCodeIgnoreCase(@Param("clientId") UUID clientId, @Param("orgId") UUID orgId, @Param("code") String code);

    @Query("""
            SELECT COUNT(a) > 0 FROM AccountingAccount a
            WHERE a.clientId = :clientId
              AND ((:orgId IS NULL AND a.orgId IS NULL) OR a.orgId = :orgId)
              AND UPPER(a.code) = UPPER(:code)
            """)
    boolean existsByClientIdAndOrgIdAndCodeIgnoreCase(@Param("clientId") UUID clientId, @Param("orgId") UUID orgId, @Param("code") String code);

    @Query("""
            SELECT COUNT(a) > 0 FROM AccountingAccount a
            WHERE a.clientId = :clientId
              AND ((:orgId IS NULL AND a.orgId IS NULL) OR a.orgId = :orgId)
              AND UPPER(a.code) = UPPER(:code)
              AND a.id <> :id
            """)
    boolean existsByClientIdAndOrgIdAndCodeIgnoreCaseAndIdNot(@Param("clientId") UUID clientId, @Param("orgId") UUID orgId, @Param("code") String code, @Param("id") UUID id);
}
