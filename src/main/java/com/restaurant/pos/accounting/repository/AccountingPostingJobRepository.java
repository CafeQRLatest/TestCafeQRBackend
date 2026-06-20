package com.restaurant.pos.accounting.repository;

import com.restaurant.pos.accounting.domain.AccountingPostingJob;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountingPostingJobRepository extends JpaRepository<AccountingPostingJob, UUID> {

    Optional<AccountingPostingJob> findByClientIdAndOrgIdAndSourceTypeAndSourceId(UUID clientId, UUID orgId, String sourceType, UUID sourceId);

    // Org-agnostic version: finds first posting job regardless of org_id value
    Optional<AccountingPostingJob> findFirstByClientIdAndSourceTypeAndSourceId(UUID clientId, String sourceType, UUID sourceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT j FROM AccountingPostingJob j
            WHERE j.clientId = :clientId
              AND (:orgId IS NULL OR j.orgId = :orgId)
              AND j.sourceType = :sourceType
              AND j.sourceId = :sourceId
            ORDER BY j.updatedAt DESC
            """)
    List<AccountingPostingJob> findLockedBySource(
            @Param("clientId") UUID clientId,
            @Param("orgId") UUID orgId,
            @Param("sourceType") String sourceType,
            @Param("sourceId") UUID sourceId);

    @Modifying
    @Query(value = """
            INSERT INTO accounting_posting_jobs (
                id, client_id, org_id, source_type, source_id, status, attempt_count,
                requested_at, created_at, updated_at, created_by, updated_by
            )
            VALUES (
                :id, :clientId, :orgId, :sourceType, :sourceId, :status, 0,
                CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SYSTEM', 'SYSTEM'
            )
            ON CONFLICT (client_id, org_id, source_type, source_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("clientId") UUID clientId,
            @Param("orgId") UUID orgId,
            @Param("sourceType") String sourceType,
            @Param("sourceId") UUID sourceId,
            @Param("status") String status);

    List<AccountingPostingJob> findByClientIdAndOrgIdAndStatusOrderByUpdatedAtDesc(UUID clientId, UUID orgId, String status);

    List<AccountingPostingJob> findByClientIdAndStatusOrderByUpdatedAtDesc(UUID clientId, String status);

    List<AccountingPostingJob> findByClientIdAndOrgId(UUID clientId, UUID orgId);

    @Modifying
    @Query("DELETE FROM AccountingPostingJob j WHERE j.clientId = :clientId AND (:orgId IS NULL OR j.orgId = :orgId)")
    int bulkDeleteByClientIdAndOrgId(@Param("clientId") UUID clientId, @Param("orgId") UUID orgId);
}
