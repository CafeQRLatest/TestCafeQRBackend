package com.restaurant.pos.client.repository;

import com.restaurant.pos.client.domain.Terminal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface TerminalRepository extends JpaRepository<Terminal, UUID> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"organization"})
    List<Terminal> findAllByClientId(UUID clientId);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"organization"})
    List<Terminal> findAllByOrgIdAndClientId(UUID orgId, UUID clientId);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"organization"})
    java.util.Optional<Terminal> findByIdAndClientId(UUID id, UUID clientId);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"organization"})
    java.util.Optional<Terminal> findByIdAndClientIdAndOrgId(UUID id, UUID clientId, UUID orgId);
    
    List<Terminal> findAllByOrgId(UUID orgId);

    java.util.Optional<Terminal> findByDeviceIdAndIsactiveAndClientId(UUID deviceId, String isactive, UUID clientId);

    java.util.Optional<Terminal> findByDeviceIdAndIsactiveAndClientIdAndIdNot(UUID deviceId, String isactive, UUID clientId, UUID id);
}
