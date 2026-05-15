package com.restaurant.pos.accounting.repository;

import com.restaurant.pos.accounting.domain.JournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID>, JpaSpecificationExecutor<JournalEntry> {

    Optional<JournalEntry> findByIdAndClientId(UUID id, UUID clientId);

    Optional<JournalEntry> findByIdAndClientIdAndOrgId(UUID id, UUID clientId, UUID orgId);

    Optional<JournalEntry> findByClientIdAndOrgIdAndSourceTypeAndSourceId(UUID clientId, UUID orgId, String sourceType, UUID sourceId);

    boolean existsByClientIdAndOrgIdAndSourceTypeAndSourceId(UUID clientId, UUID orgId, String sourceType, UUID sourceId);
}
