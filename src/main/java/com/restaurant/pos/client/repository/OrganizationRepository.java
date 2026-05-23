package com.restaurant.pos.client.repository;

import com.restaurant.pos.client.domain.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
    List<Organization> findAllByClientId(UUID clientId);
    List<Organization> findAllByClientIdAndIdIn(UUID clientId, Collection<UUID> ids);
    java.util.Optional<Organization> findByIdAndClientId(UUID id, UUID clientId);
}
