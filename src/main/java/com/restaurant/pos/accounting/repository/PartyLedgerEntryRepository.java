package com.restaurant.pos.accounting.repository;

import com.restaurant.pos.accounting.domain.PartyLedgerEntry;
import com.restaurant.pos.accounting.domain.PartyType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartyLedgerEntryRepository extends JpaRepository<PartyLedgerEntry, UUID> {

    List<PartyLedgerEntry> findByClientIdAndPartyTypeAndPartyIdOrderByEntryDateDesc(
            UUID clientId,
            PartyType partyType,
            UUID partyId
    );

    List<PartyLedgerEntry> findByClientIdAndOrgIdAndPartyTypeAndPartyIdOrderByEntryDateDesc(
            UUID clientId,
            UUID orgId,
            PartyType partyType,
            UUID partyId
    );

    Optional<PartyLedgerEntry> findTopByClientIdAndOrgIdAndPartyTypeAndPartyIdOrderByEntryDateDescCreatedAtDesc(
            UUID clientId,
            UUID orgId,
            PartyType partyType,
            UUID partyId
    );

    @Modifying
    @Query(value = """
            DELETE FROM party_ledger_entries
            WHERE journal_entry_id IN (
                SELECT id FROM journal_entries
                WHERE client_id = :clientId
                  AND (:orgId IS NULL OR org_id = :orgId)
                  AND COALESCE(auto_posted, false) = true
            )
            """, nativeQuery = true)
    int bulkDeleteForAutoPostedJournalsByClientIdAndOrgId(@Param("clientId") UUID clientId, @Param("orgId") UUID orgId);
}
