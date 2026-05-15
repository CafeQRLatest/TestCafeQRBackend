package com.restaurant.pos.accounting.repository;

import com.restaurant.pos.accounting.domain.AccountingPostingJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountingPostingJobRepository extends JpaRepository<AccountingPostingJob, UUID> {

    Optional<AccountingPostingJob> findByClientIdAndOrgIdAndSourceTypeAndSourceId(UUID clientId, UUID orgId, String sourceType, UUID sourceId);

    List<AccountingPostingJob> findByClientIdAndOrgIdAndStatusOrderByUpdatedAtDesc(UUID clientId, UUID orgId, String status);

    List<AccountingPostingJob> findByClientIdAndOrgId(UUID clientId, UUID orgId);

    void deleteByClientIdAndOrgId(UUID clientId, UUID orgId);
}
