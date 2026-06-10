package com.restaurant.pos.accounting.repository;

import com.restaurant.pos.accounting.domain.JournalEntry;
import com.restaurant.pos.accounting.domain.JournalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID>, JpaSpecificationExecutor<JournalEntry> {

    interface AccountMovementProjection {
        UUID getAccountId();
        BigDecimal getDebit();
        BigDecimal getCredit();
    }

    interface PostedSourceProjection {
        String getSourceType();
        UUID getSourceId();
    }

    Optional<JournalEntry> findByIdAndClientId(UUID id, UUID clientId);

    Optional<JournalEntry> findByIdAndClientIdAndOrgId(UUID id, UUID clientId, UUID orgId);

    Optional<JournalEntry> findByClientIdAndOrgIdAndSourceTypeAndSourceId(UUID clientId, UUID orgId, String sourceType, UUID sourceId);

    // Org-agnostic versions
    Optional<JournalEntry> findFirstByClientIdAndSourceTypeAndSourceId(UUID clientId, String sourceType, UUID sourceId);

    boolean existsByClientIdAndOrgIdAndSourceTypeAndSourceId(UUID clientId, UUID orgId, String sourceType, UUID sourceId);

    boolean existsByClientIdAndSourceTypeAndSourceId(UUID clientId, String sourceType, UUID sourceId);

    @Query(value = """
            SELECT DISTINCT j.* FROM journal_entries j
            LEFT JOIN journal_lines l ON j.id = l.journal_entry_id
            WHERE j.client_id = :clientId
              AND (CAST(:orgId AS UUID) IS NULL OR j.org_id = CAST(:orgId AS UUID))
              AND j.source_type = :sourceType
              AND j.source_id = :sourceId
              AND j.status = :#{#status.name()}
              AND COALESCE(UPPER(j.isactive), 'Y') <> 'N'
            ORDER BY j.entry_date DESC
            """, nativeQuery = true)
    List<JournalEntry> findActiveBySource(
            @Param("clientId") UUID clientId,
            @Param("orgId") UUID orgId,
            @Param("sourceType") String sourceType,
            @Param("sourceId") UUID sourceId,
            @Param("status") JournalStatus status);

    @EntityGraph(attributePaths = {"lines"})
    @Query("""
            SELECT j FROM JournalEntry j
            WHERE j.clientId = :clientId
              AND (:orgId IS NULL OR j.orgId = :orgId)
              AND j.sourceType = :sourceType
              AND j.sourceId = :sourceId
              AND j.status = :status
              AND COALESCE(UPPER(j.isactive), 'Y') <> 'N'
            ORDER BY j.entryDate DESC
            """)
    List<JournalEntry> findActiveBySourceWithLines(
            @Param("clientId") UUID clientId,
            @Param("orgId") UUID orgId,
            @Param("sourceType") String sourceType,
            @Param("sourceId") UUID sourceId,
            @Param("status") JournalStatus status);

    @Query(value = """
            SELECT l.account_id AS accountId,
                   COALESCE(SUM(l.debit), 0) AS debit,
                   COALESCE(SUM(l.credit), 0) AS credit
            FROM journal_entries j
            JOIN journal_lines l ON j.id = l.journal_entry_id
            WHERE j.client_id = :clientId
              AND (CAST(:orgId AS UUID) IS NULL OR j.org_id = CAST(:orgId AS UUID))
              AND (CAST(:terminalId AS UUID) IS NULL OR j.terminal_id = CAST(:terminalId AS UUID))
              AND (CAST(:from AS TIMESTAMP) IS NULL OR j.entry_date >= CAST(:from AS TIMESTAMP))
              AND (CAST(:to AS TIMESTAMP) IS NULL OR j.entry_date <= CAST(:to AS TIMESTAMP))
              AND j.status = :#{#status.name()}
              AND COALESCE(UPPER(j.isactive), 'Y') <> 'N'
            GROUP BY l.account_id
            """, nativeQuery = true)
    List<AccountMovementProjection> sumLineMovements(
            @Param("clientId") UUID clientId,
            @Param("orgId") UUID orgId,
            @Param("terminalId") UUID terminalId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("status") JournalStatus status);

    @Query(value = """
            SELECT COUNT(j.id)
            FROM journal_entries j
            WHERE j.client_id = :clientId
              AND (CAST(:orgId AS UUID) IS NULL OR j.org_id = CAST(:orgId AS UUID))
              AND (CAST(:terminalId AS UUID) IS NULL OR j.terminal_id = CAST(:terminalId AS UUID))
              AND (CAST(:from AS TIMESTAMP) IS NULL OR j.entry_date >= CAST(:from AS TIMESTAMP))
              AND (CAST(:to AS TIMESTAMP) IS NULL OR j.entry_date <= CAST(:to AS TIMESTAMP))
              AND j.status = :#{#status.name()}
              AND COALESCE(UPPER(j.isactive), 'Y') <> 'N'
            """, nativeQuery = true)
    long countPostedActive(
            @Param("clientId") UUID clientId,
            @Param("orgId") UUID orgId,
            @Param("terminalId") UUID terminalId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("status") JournalStatus status);

    @Query(value = """
            SELECT j.source_type AS sourceType,
                   j.source_id AS sourceId
            FROM journal_entries j
            WHERE j.client_id = :clientId
              AND (CAST(:orgId AS UUID) IS NULL OR j.org_id = CAST(:orgId AS UUID))
              AND (CAST(:terminalId AS UUID) IS NULL OR j.terminal_id = CAST(:terminalId AS UUID))
              AND (CAST(:from AS TIMESTAMP) IS NULL OR j.entry_date >= CAST(:from AS TIMESTAMP))
              AND (CAST(:to AS TIMESTAMP) IS NULL OR j.entry_date <= CAST(:to AS TIMESTAMP))
              AND j.status = :#{#status.name()}
              AND COALESCE(UPPER(j.isactive), 'Y') <> 'N'
              AND j.source_id IS NOT NULL
              AND j.source_type IN (:sourceTypes)
            """, nativeQuery = true)
    List<PostedSourceProjection> findPostedSources(
            @Param("clientId") UUID clientId,
            @Param("orgId") UUID orgId,
            @Param("terminalId") UUID terminalId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("status") JournalStatus status,
            @Param("sourceTypes") Set<String> sourceTypes);

    @Modifying
    @Query(value = "DELETE FROM journal_lines WHERE journal_entry_id IN (SELECT id FROM journal_entries WHERE client_id = :clientId AND org_id = :orgId)", nativeQuery = true)
    void bulkDeleteLinesByClientIdAndOrgId(@Param("clientId") UUID clientId, @Param("orgId") UUID orgId);

    @Modifying
    @Query(value = "DELETE FROM journal_entries WHERE client_id = :clientId AND org_id = :orgId", nativeQuery = true)
    int bulkDeleteByClientIdAndOrgId(@Param("clientId") UUID clientId, @Param("orgId") UUID orgId);

    @Modifying
    @Query(value = """
            DELETE FROM journal_lines
            WHERE journal_entry_id IN (
                SELECT id FROM journal_entries
                WHERE client_id = :clientId
                  AND (CAST(:orgId AS UUID) IS NULL OR org_id = CAST(:orgId AS UUID))
                  AND COALESCE(auto_posted, false) = true
            )
            """, nativeQuery = true)
    void bulkDeleteAutoPostedLinesByClientIdAndOrgId(@Param("clientId") UUID clientId, @Param("orgId") UUID orgId);

    @Modifying
    @Query(value = """
            DELETE FROM journal_entries
            WHERE client_id = :clientId
              AND (CAST(:orgId AS UUID) IS NULL OR org_id = CAST(:orgId AS UUID))
              AND COALESCE(auto_posted, false) = true
            """, nativeQuery = true)
    int bulkDeleteAutoPostedByClientIdAndOrgId(@Param("clientId") UUID clientId, @Param("orgId") UUID orgId);
}
