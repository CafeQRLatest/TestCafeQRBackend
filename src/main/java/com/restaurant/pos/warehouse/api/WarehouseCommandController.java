package com.restaurant.pos.warehouse.api;

import com.restaurant.pos.common.dto.ApiResponse;
import com.restaurant.pos.common.security.StaffAccess;
import com.restaurant.pos.subscription.annotation.RequireModule;
import com.restaurant.pos.subscription.domain.ModuleName;
import com.restaurant.pos.warehouse.command.WarehouseCommand;
import com.restaurant.pos.warehouse.command.WarehouseCommandService;
import com.restaurant.pos.warehouse.domain.Warehouse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * CQRS Command Controller for Warehouse operations.
 * Handles state mutations: CREATE, UPDATE, DELETE.
 */
@Slf4j
@StaffAccess
@RestController
@RequestMapping("/api/v1/warehouses")
@RequiredArgsConstructor
@Validated
@RequireModule(ModuleName.INVENTORY)
@Tag(name = "Warehouse Commands", description = "Endpoints for creating, updating, and deleting Warehouses.")
public class WarehouseCommandController {

    private final WarehouseCommandService commandService;

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    @Operation(
        summary = "Create Warehouse",
        description = "Creates a new Warehouse for the current branch. The first warehouse is automatically set as default."
    )
    public ResponseEntity<ApiResponse<Warehouse>> createWarehouse(
            @Parameter(description = "Warehouse details", required = true)
            @RequestBody WarehouseCommand command) {

        log.info("Creating warehouse | name={}", command.getName());
        Warehouse created = commandService.createWarehouse(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    @Operation(
        summary = "Update Warehouse",
        description = "Updates an existing Warehouse by ID. Enforces default-warehouse promotion logic."
    )
    public ResponseEntity<ApiResponse<Warehouse>> updateWarehouse(
            @Parameter(description = "UUID of the Warehouse to update", required = true) @PathVariable UUID id,
            @Parameter(description = "Updated warehouse details", required = true) @RequestBody WarehouseCommand command) {

        log.info("Updating warehouse | id={} | name={}", id, command.getName());
        Warehouse updated = commandService.updateWarehouse(id, command);
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    @Operation(
        summary = "Delete Warehouse",
        description = "Deletes a Warehouse. If the deleted warehouse was the default, the next one is automatically promoted."
    )
    public ResponseEntity<ApiResponse<Void>> deleteWarehouse(
            @Parameter(description = "UUID of the Warehouse to delete", required = true) @PathVariable UUID id) {

        log.info("Deleting warehouse | id={}", id);
        commandService.deleteWarehouse(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
