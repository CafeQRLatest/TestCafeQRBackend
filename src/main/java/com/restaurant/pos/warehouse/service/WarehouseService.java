package com.restaurant.pos.warehouse.service;

import com.restaurant.pos.common.exception.ResourceNotFoundException;
import com.restaurant.pos.common.service.BranchContextService;
import com.restaurant.pos.common.tenant.TenantContext;
import com.restaurant.pos.common.util.SecurityUtils;
import com.restaurant.pos.warehouse.domain.Warehouse;
import com.restaurant.pos.warehouse.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final BranchContextService branchContext;

    public Optional<Warehouse> findDefaultWarehouse(UUID clientId, UUID orgId) {
        if (orgId != null) {
            List<Warehouse> defaults = warehouseRepository.findDefaultWarehousesForOrg(clientId, orgId);
            if (!defaults.isEmpty()) {
                return Optional.of(defaults.get(0));
            }
        }
        return warehouseRepository.findFirstByClientIdAndIsDefaultTrue(clientId);
    }

    public List<Warehouse> getWarehouses(UUID orgId) {
        UUID clientId = TenantContext.getCurrentTenant();
        UUID effectiveOrgId = orgId != null ? orgId : TenantContext.getCurrentOrg();
        if (SecurityUtils.isSuperAdmin() && orgId == null) {
            return warehouseRepository.findByClientIdOrderByCreatedAtDesc(clientId);
        }
        return warehouseRepository.findByClientIdAndOrgIdOrGlobalOrderByCreatedAtDesc(clientId, effectiveOrgId);
    }

    public List<Warehouse> getWarehouses() {
        return getWarehouses(null);
    }

    public Warehouse getWarehouse(UUID id) {
        UUID clientId = TenantContext.getCurrentTenant();
        if (SecurityUtils.isSuperAdmin()) {
            return warehouseRepository.findByIdAndClientId(id, clientId)
                    .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found"));
        }
        return warehouseRepository.findByIdAndClientIdAndOrgIdOrGlobal(id, clientId, TenantContext.getCurrentOrg())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found for ID: " + id));
    }

    @Transactional
    public Warehouse saveWarehouse(Warehouse warehouse) {
        UUID clientId = TenantContext.getCurrentTenant();
        UUID orgId = branchContext.requireWriteOrgId(warehouse.getOrgId());
        warehouse.setClientId(clientId);
        warehouse.setOrgId(orgId);

        if (warehouse.isDefault()) {
            warehouseRepository.unsetOtherDefaultsForOrg(clientId, orgId, warehouse.getId());
        }

        return warehouseRepository.save(warehouse);
    }

    @Transactional
    public void deleteWarehouse(UUID id) {
        Warehouse warehouse = getWarehouse(id);
        if (warehouse != null) {
            warehouseRepository.delete(warehouse);
        }
    }
}
