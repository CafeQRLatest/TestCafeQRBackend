package com.restaurant.pos.table.repository;

import com.restaurant.pos.table.domain.RestaurantTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RestaurantTableRepository extends JpaRepository<RestaurantTable, UUID> {

    List<RestaurantTable> findByClientIdOrderByDisplayOrderAscTableNumberAsc(UUID clientId);

    List<RestaurantTable> findByClientIdAndOrgIdOrderByDisplayOrderAscTableNumberAsc(UUID clientId, UUID orgId);

    Optional<RestaurantTable> findByIdAndClientId(UUID id, UUID clientId);

    Optional<RestaurantTable> findByIdAndClientIdAndOrgId(UUID id, UUID clientId, UUID orgId);

    List<RestaurantTable> findByClientIdAndIsactiveOrderByDisplayOrderAscTableNumberAsc(UUID clientId, String isactive);

    List<RestaurantTable> findByClientIdAndOrgIdAndIsactiveOrderByDisplayOrderAscTableNumberAsc(UUID clientId, UUID orgId, String isactive);

    List<RestaurantTable> findByClientIdAndUpdatedAtAfterOrderByDisplayOrderAscTableNumberAsc(UUID clientId, LocalDateTime updatedAfter);

    List<RestaurantTable> findByClientIdAndOrgIdAndUpdatedAtAfterOrderByDisplayOrderAscTableNumberAsc(UUID clientId, UUID orgId, LocalDateTime updatedAfter);
}
