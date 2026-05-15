package com.restaurant.pos.accounting.repository;

import com.restaurant.pos.accounting.domain.PartyLedgerEntry;
import com.restaurant.pos.accounting.domain.PartyType;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
