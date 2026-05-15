package com.restaurant.pos.accounting.repository;

import com.restaurant.pos.accounting.domain.AccountingPostingJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountingPostingJobRepository extends JpaRepository<AccountingPostingJob, UUID> {

    Optional<AccountingPostingJob> findByClientIdAndOrgIdAndSourceTypeAndSourceId(UUID clientId, UUID orgId, String sourceType, UUID sourceId);

    List<AccountingPostingJob> findByClientIdAndOrgIdAndStatusOrderByUpdatedAtDesc(UUID clientId, UUID orgId, String status);

    List<AccountingPostingJob> findByClientIdAndOrgId(UUID clientId, UUID orgId);

    @Modifying
    @Query(value = "DELETE FROM accounting_posting_jobs WHERE client_id = :clientId AND org_id = :orgId", nativeQuery = true)
    int bulkDeleteByClientIdAndOrgId(@Param("clientId") UUID clientId, @Param("orgId") UUID orgId);
}
