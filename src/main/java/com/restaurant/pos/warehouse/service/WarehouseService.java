package com.restaurant.pos.warehouse.service;

import com.restaurant.pos.common.exception.BusinessException;
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
            List<Warehouse> orgWarehouses = warehouseRepository.findByClientIdAndOrgIdOrderByCreatedAtDesc(clientId, orgId);
            if (!orgWarehouses.isEmpty()) {
                return Optional.of(orgWarehouses.get(0));
            }
        }
        return warehouseRepository.findFirstByClientIdAndIsDefaultTrue(clientId);
    }

    public List<Warehouse> getWarehouses(UUID orgId) {
        UUID clientId = TenantContext.getCurrentTenant();
        UUID effectiveOrgId = orgId != null ? orgId : TenantContext.getCurrentOrg();
        List<Warehouse> list;
        if (SecurityUtils.isSuperAdmin() && orgId == null) {
            list = warehouseRepository.findByClientIdOrderByCreatedAtDesc(clientId);
        } else {
            list = warehouseRepository.findByClientIdAndOrgIdOrGlobalOrderByCreatedAtDesc(clientId, effectiveOrgId);
        }

        if (effectiveOrgId != null) {
            List<Warehouse> orgWhs = list.stream()
                    .filter(w -> effectiveOrgId.equals(w.getOrgId()))
                    .toList();
            if (orgWhs.size() == 1 && !orgWhs.get(0).isDefault()) {
                Warehouse single = orgWhs.get(0);
                single.setDefault(true);
                warehouseRepository.save(single);
            }
        }
        return list;
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
        // Always take clientId and orgId from TenantContext (set by JWT filter).
        // Never trust anything the frontend sends for these fields.
        UUID clientId = TenantContext.getCurrentTenant();
        UUID orgId = TenantContext.getCurrentOrg();

        if (orgId == null) {
            throw new BusinessException(
                "A branch must be selected before creating a warehouse. " +
                "Please select an active branch from the branch picker."
            );
        }

        warehouse.setClientId(clientId);
        warehouse.setOrgId(orgId);


        List<Warehouse> existingForOrg = warehouseRepository.findByClientIdAndOrgIdOrderByCreatedAtDesc(clientId, orgId);

        boolean isOnlyWarehouse = existingForOrg.isEmpty() ||
                (existingForOrg.size() == 1 && (warehouse.getId() == null || existingForOrg.get(0).getId().equals(warehouse.getId())));

        if (isOnlyWarehouse || warehouse.isDefault()) {
            warehouse.setDefault(true);
            warehouseRepository.unsetOtherDefaultsForOrg(clientId, orgId, warehouse.getId());
        } else {
            boolean hasOtherDefault = existingForOrg.stream()
                    .anyMatch(w -> w.isDefault() && !w.getId().equals(warehouse.getId()));
            if (!hasOtherDefault) {
                warehouse.setDefault(true);
            }
        }

        return warehouseRepository.save(warehouse);
    }

    @Transactional
    public void deleteWarehouse(UUID id) {
        Warehouse warehouse = getWarehouse(id);
        if (warehouse != null) {
            boolean wasDefault = warehouse.isDefault();
            UUID clientId = warehouse.getClientId();
            UUID orgId = warehouse.getOrgId();

            warehouseRepository.delete(warehouse);

            if (wasDefault && orgId != null) {
                List<Warehouse> remaining = warehouseRepository.findByClientIdAndOrgIdOrderByCreatedAtDesc(clientId, orgId);
                if (!remaining.isEmpty()) {
                    Warehouse nextDefault = remaining.get(0);
                    nextDefault.setDefault(true);
                    warehouseRepository.save(nextDefault);
                }
            }
        }
    }
}
