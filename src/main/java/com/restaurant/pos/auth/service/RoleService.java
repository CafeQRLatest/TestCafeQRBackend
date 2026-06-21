package com.restaurant.pos.auth.service;

import com.restaurant.pos.auth.domain.RoleEntity;
import com.restaurant.pos.auth.domain.Permission;
import com.restaurant.pos.auth.domain.Menu;
import com.restaurant.pos.auth.repository.RoleRepository;
import com.restaurant.pos.auth.repository.PermissionRepository;
import com.restaurant.pos.auth.repository.MenuRepository;
import com.restaurant.pos.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository repository;
    private final PermissionRepository permissionRepository;
    private final MenuRepository menuRepository;

    public List<RoleEntity> getMyRoles() {
        UUID currentTenant = TenantContext.getCurrentTenant();
        return repository.findAll().stream()
                .filter(r -> r.getClientId() == null || r.getClientId().equals(currentTenant))
                .collect(Collectors.toMap(
                    RoleEntity::getName,
                    r -> r,
                    (existing, replacement) -> {
                        // Prioritize client-specific role over global role
                        if (replacement.getClientId() != null) {
                            return replacement;
                        }
                        return existing;
                    }
                ))
                .values().stream()
                .collect(Collectors.toList());
    }

    public List<Permission> getAllPermissions() {
        return permissionRepository.findAll();
    }

    public List<Menu> getAllMenus() {
        return menuRepository.findByIsactive("Y");
    }

    @Transactional
    public RoleEntity saveRole(RoleEntity role) {
        if (role.getClientId() == null) {
            role.setClientId(TenantContext.getCurrentTenant());
        }
        return repository.save(role);
    }

    @Transactional
    public void deleteRole(UUID id) {
        repository.deleteById(java.util.Objects.requireNonNull(id));
    }
}
