package com.restaurant.pos.subscription.repository;

import com.restaurant.pos.subscription.domain.ClientSubscriptionModule;
import com.restaurant.pos.subscription.domain.ModuleName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientSubscriptionModuleRepository extends JpaRepository<ClientSubscriptionModule, UUID> {

    List<ClientSubscriptionModule> findByClientId(UUID clientId);

    List<ClientSubscriptionModule> findByClientIdAndOrgId(UUID clientId, UUID orgId);

    Optional<ClientSubscriptionModule> findByClientIdAndOrgIdAndModuleName(UUID clientId, UUID orgId, ModuleName moduleName);

    Optional<ClientSubscriptionModule> findByClientIdAndModuleNameAndOrgIdIsNull(UUID clientId, ModuleName moduleName);

    @Query("SELECT m FROM ClientSubscriptionModule m WHERE m.clientId = :clientId AND (m.orgId = :orgId OR m.orgId IS NULL)")
    List<ClientSubscriptionModule> findByClientIdAndOrgIdOrGlobal(@Param("clientId") UUID clientId, @Param("orgId") UUID orgId);
}
