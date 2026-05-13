package com.restaurant.pos.common.repository;

import com.restaurant.pos.common.entity.SystemConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SystemConfigurationRepository extends JpaRepository<SystemConfiguration, UUID> {
    Optional<SystemConfiguration> findFirstByClientIdAndOrgIdIsNull(UUID clientId);

    Optional<SystemConfiguration> findFirstByClientIdIsNullAndOrgIdIsNullOrderByCreatedAtAsc();
}
