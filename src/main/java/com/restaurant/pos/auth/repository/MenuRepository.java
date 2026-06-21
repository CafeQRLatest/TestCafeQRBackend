package com.restaurant.pos.auth.repository;

import com.restaurant.pos.auth.domain.Menu;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface MenuRepository extends JpaRepository<Menu, UUID> {
    List<Menu> findByIsactive(String isactive);
    List<Menu> findByParentIdIn(List<UUID> parentIds);
}
