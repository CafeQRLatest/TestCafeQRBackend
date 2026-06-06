package com.restaurant.pos.print.repository;

import com.restaurant.pos.print.domain.PrintStation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PrintStationRepository extends JpaRepository<PrintStation, UUID> {
    Optional<PrintStation> findByClientIdAndTerminalId(UUID clientId, UUID terminalId);
    Optional<PrintStation> findByStationTokenHashAndIsactive(String tokenHash, String isactive);
    Optional<PrintStation> findByPairingCodeHashAndIsactive(String pairingCodeHash, String isactive);
    List<PrintStation> findAllByClientIdOrderByCreatedAtDesc(UUID clientId);
    List<PrintStation> findAllByClientIdAndOrgIdAndFallbackForBranchTrueAndIsactive(
            UUID clientId,
            UUID orgId,
            String isactive
    );
}
