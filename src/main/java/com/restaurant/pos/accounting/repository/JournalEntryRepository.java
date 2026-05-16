package com.restaurant.pos.accounting.repository;

import com.restaurant.pos.accounting.domain.JournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID>, JpaSpecificationExecutor<JournalEntry> {

    Optional<JournalEntry> findByIdAndClientId(UUID id, UUID clientId);

    Optional<JournalEntry> findByIdAndClientIdAndOrgId(UUID id, UUID clientId, UUID orgId);

    Optional<JournalEntry> findByClientIdAndOrgIdAndSourceTypeAndSourceId(UUID clientId, UUID orgId, String sourceType, UUID sourceId);

    // Org-agnostic versions
    Optional<JournalEntry> findFirstByClientIdAndSourceTypeAndSourceId(UUID clientId, String sourceType, UUID sourceId);

    boolean existsByClientIdAndOrgIdAndSourceTypeAndSourceId(UUID clientId, UUID orgId, String sourceType, UUID sourceId);

    boolean existsByClientIdAndSourceTypeAndSourceId(UUID clientId, String sourceType, UUID sourceId);

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
                  AND (:orgId IS NULL OR org_id = :orgId)
                  AND COALESCE(auto_posted, false) = true
            )
            """, nativeQuery = true)
    void bulkDeleteAutoPostedLinesByClientIdAndOrgId(@Param("clientId") UUID clientId, @Param("orgId") UUID orgId);

    @Modifying
    @Query(value = """
            DELETE FROM journal_entries
            WHERE client_id = :clientId
              AND (:orgId IS NULL OR org_id = :orgId)
              AND COALESCE(auto_posted, false) = true
            """, nativeQuery = true)
    int bulkDeleteAutoPostedByClientIdAndOrgId(@Param("clientId") UUID clientId, @Param("orgId") UUID orgId);
}
