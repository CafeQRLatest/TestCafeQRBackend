package com.restaurant.pos.print.repository;

import com.restaurant.pos.print.domain.PrintConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PrintConfigurationRepository extends JpaRepository<PrintConfiguration, UUID> {
    Optional<PrintConfiguration> findByClientIdAndScopeTypeAndScopeId(UUID clientId, String scopeType, UUID scopeId);
    Optional<PrintConfiguration> findByClientIdAndScopeTypeAndScopeIdIsNull(UUID clientId, String scopeType);
    List<PrintConfiguration> findAllByClientId(UUID clientId);
}
