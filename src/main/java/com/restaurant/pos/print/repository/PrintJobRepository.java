package com.restaurant.pos.print.repository;

import com.restaurant.pos.print.domain.PrintJob;
import com.restaurant.pos.print.domain.PrintJobKind;
import com.restaurant.pos.print.domain.PrintJobStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PrintJobRepository extends JpaRepository<PrintJob, UUID> {

    Optional<PrintJob> findByClientIdAndDedupeKey(UUID clientId, String dedupeKey);

    List<PrintJob> findByClientIdAndOrderIdOrderByCreatedAtDesc(UUID clientId, UUID orderId);

    List<PrintJob> findByClientIdAndOrderIdAndJobKindAndStatusInOrderByCreatedAtDesc(
            UUID clientId,
            UUID orderId,
            PrintJobKind jobKind,
            Collection<PrintJobStatus> statuses
    );

    @Query(value = """
            SELECT *
            FROM print_jobs
            WHERE client_id = :clientId
              AND job_kind IN ('KOT', 'BILL', 'INVOICE', 'TEST')
              AND status IN (
                    'PENDING', 'CLAIMED', 'LEASED', 'LOCAL_QUEUED', 'SPOOLING',
                    'SPOOLED', 'COMPLETED', 'PRINTED', 'FAILED', 'RETRY',
                    'RETRY_WAIT', 'HELD_AMBIGUOUS'
              )
            ORDER BY created_at DESC
            LIMIT 100
            """, nativeQuery = true)
    List<PrintJob> findRecentValid(@Param("clientId") UUID clientId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT p FROM PrintJob p
            WHERE p.clientId = :clientId
              AND (:orgId IS NULL OR p.orgId = :orgId)
              AND p.status IN :statuses
              AND p.jobKind IN :jobKinds
              AND (p.nextAttemptAt IS NULL OR p.nextAttemptAt <= :now)
            ORDER BY p.createdAt ASC
            """)
    List<PrintJob> findClaimable(
            @Param("clientId") UUID clientId,
            @Param("orgId") UUID orgId,
            @Param("statuses") Collection<PrintJobStatus> statuses,
            @Param("jobKinds") Collection<PrintJobKind> jobKinds,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT p FROM PrintJob p
            WHERE p.clientId = :clientId
              AND p.orgId = :orgId
              AND (
                    p.status IN :readyStatuses
                    OR (p.status = :leasedStatus AND p.leaseExpiresAt < :now)
              )
              AND (p.nextAttemptAt IS NULL OR p.nextAttemptAt <= :now)
              AND (
                    p.targetTerminalId = :terminalId
                    OR (p.targetTerminalId IS NULL AND p.sourceTerminalId = :terminalId)
                    OR (:fallback = true AND p.targetTerminalId IS NULL AND p.sourceTerminalId IS NULL)
              )
            ORDER BY p.createdAt ASC
            """)
    List<PrintJob> findStationClaimable(
            @Param("clientId") UUID clientId,
            @Param("orgId") UUID orgId,
            @Param("terminalId") UUID terminalId,
            @Param("fallback") boolean fallback,
            @Param("readyStatuses") Collection<PrintJobStatus> readyStatuses,
            @Param("leasedStatus") PrintJobStatus leasedStatus,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    List<PrintJob> findAllByLeasedByStationIdAndStatusIn(
            UUID stationId,
            Collection<PrintJobStatus> statuses
    );
}
