package com.restaurant.pos.accounting.repository;

import com.restaurant.pos.accounting.domain.AccountingAccountMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountingAccountMappingRepository extends JpaRepository<AccountingAccountMapping, UUID> {

    @Query("""
            SELECT m FROM AccountingAccountMapping m
            WHERE m.clientId = :clientId
              AND ((:orgId IS NULL AND m.orgId IS NULL) OR m.orgId = :orgId)
              AND m.isactive = :isactive
            ORDER BY m.mappingKey ASC
            """)
    List<AccountingAccountMapping> findByClientIdAndOrgIdAndIsactiveOrderByMappingKeyAsc(@Param("clientId") UUID clientId, @Param("orgId") UUID orgId, @Param("isactive") String isactive);

    @Query("""
            SELECT m FROM AccountingAccountMapping m
            WHERE m.clientId = :clientId
              AND ((:orgId IS NULL AND m.orgId IS NULL) OR m.orgId = :orgId)
              AND UPPER(m.mappingKey) = UPPER(:mappingKey)
            """)
    Optional<AccountingAccountMapping> findByClientIdAndOrgIdAndMappingKeyIgnoreCase(@Param("clientId") UUID clientId, @Param("orgId") UUID orgId, @Param("mappingKey") String mappingKey);
}
