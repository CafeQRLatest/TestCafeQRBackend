package com.restaurant.pos.auth.repository;

import com.restaurant.pos.auth.domain.Menu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface MenuRepository extends JpaRepository<Menu, UUID> {
    List<Menu> findByIsactive(String isactive);
    List<Menu> findByParentIdIn(List<UUID> parentIds);

    @Query("SELECT m FROM Menu m WHERE m.isactive = :isactive AND (m.clientId IS NULL OR m.clientId = :clientId)")
    List<Menu> findByIsactiveAndClientId(@Param("isactive") String isactive, @Param("clientId") UUID clientId);
}
