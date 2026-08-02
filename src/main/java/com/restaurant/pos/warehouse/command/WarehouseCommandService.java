package com.restaurant.pos.warehouse.command;

import com.restaurant.pos.common.exception.ResourceNotFoundException;
import com.restaurant.pos.common.service.BranchContextService;
import com.restaurant.pos.common.tenant.TenantContext;
import com.restaurant.pos.warehouse.domain.Warehouse;
import com.restaurant.pos.warehouse.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * CQRS Command Service — handles all write-side operations for Warehouse.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WarehouseCommandService {

    private final WarehouseRepository warehouseRepository;
    private final BranchContextService branchContext;

    /**
     * Creates a new Warehouse for the current branch, enforcing default logic.
     */
    @Transactional
    public Warehouse createWarehouse(WarehouseCommand command) {
        UUID clientId = TenantContext.getCurrentTenant();
        UUID orgId    = branchContext.requireWriteOrgId(command.getOrgId());

        Warehouse warehouse = Warehouse.builder()
                .clientId(clientId)
                .orgId(orgId)
                .name(command.getName())
                .code(command.getCode())
                .address(command.getAddress())
                .managerName(command.getManagerName())
                .managerPhone(command.getManagerPhone())
                .isDefault(command.isDefault())
                .isactive(command.getIsactive() != null ? command.getIsactive() : "Y")
                .build();

        return saveWithDefaultLogic(warehouse, clientId, orgId);
    }

    /**
     * Updates an existing Warehouse identified by {@code id}.
     */
    @Transactional
    public Warehouse updateWarehouse(UUID id, WarehouseCommand command) {
        UUID clientId = TenantContext.getCurrentTenant();

        Warehouse warehouse = warehouseRepository.findByIdAndClientId(id, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found for ID: " + id));

        UUID orgId = branchContext.requireWriteOrgId(command.getOrgId() != null ? command.getOrgId() : warehouse.getOrgId());

        warehouse.setName(command.getName() != null ? command.getName() : warehouse.getName());
        warehouse.setCode(command.getCode() != null ? command.getCode() : warehouse.getCode());
        warehouse.setAddress(command.getAddress() != null ? command.getAddress() : warehouse.getAddress());
        warehouse.setManagerName(command.getManagerName() != null ? command.getManagerName() : warehouse.getManagerName());
        warehouse.setManagerPhone(command.getManagerPhone() != null ? command.getManagerPhone() : warehouse.getManagerPhone());
        if (command.getIsDefault() != null) {
            warehouse.setDefault(command.isDefault());
        }
        if (command.getIsactive() != null) {
            warehouse.setIsactive(command.getIsactive());
        }

        return saveWithDefaultLogic(warehouse, clientId, orgId);
    }

    /**
     * Deletes a Warehouse and promotes the next one as default when needed.
     */
    @Transactional
    public void deleteWarehouse(UUID id) {
        UUID clientId = TenantContext.getCurrentTenant();
        Warehouse warehouse = warehouseRepository.findByIdAndClientId(id, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found for ID: " + id));

        boolean wasDefault = warehouse.isDefault();
        UUID orgId = warehouse.getOrgId();

        warehouseRepository.delete(warehouse);
        log.info("Deleted warehouse id={} clientId={} orgId={}", id, clientId, orgId);

        if (wasDefault && orgId != null) {
            List<Warehouse> remaining = warehouseRepository.findByClientIdAndOrgIdOrderByCreatedAtDesc(clientId, orgId);
            if (!remaining.isEmpty()) {
                Warehouse nextDefault = remaining.get(0);
                nextDefault.setDefault(true);
                warehouseRepository.save(nextDefault);
                log.info("Promoted warehouse id={} as new default for orgId={}", nextDefault.getId(), orgId);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Applies default-warehouse promotion logic then persists the entity.
     * Rule: if only one warehouse exists for the org, or the caller requests default,
     * promote it and strip the flag from all others.
     */
    private Warehouse saveWithDefaultLogic(Warehouse warehouse, UUID clientId, UUID orgId) {
        List<Warehouse> existingForOrg = warehouseRepository
                .findByClientIdAndOrgIdOrderByCreatedAtDesc(clientId, orgId);

        boolean isOnlyWarehouse = existingForOrg.isEmpty() ||
                (existingForOrg.size() == 1 &&
                 (warehouse.getId() == null || existingForOrg.get(0).getId().equals(warehouse.getId())));

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
}
