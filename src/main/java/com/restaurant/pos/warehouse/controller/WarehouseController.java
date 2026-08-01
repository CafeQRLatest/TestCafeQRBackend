package com.restaurant.pos.warehouse.controller;

import com.restaurant.pos.common.dto.ApiResponse;
import com.restaurant.pos.warehouse.domain.Warehouse;
import com.restaurant.pos.warehouse.service.WarehouseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/warehouses")
@RequiredArgsConstructor
@com.restaurant.pos.subscription.annotation.RequireModule(com.restaurant.pos.subscription.domain.ModuleName.INVENTORY)
public class WarehouseController {

    private final WarehouseService warehouseService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<Warehouse>>> getWarehouses(@RequestParam(required = false) UUID orgId) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.getWarehouses(orgId)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Warehouse>> getWarehouse(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.getWarehouse(id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Warehouse>> createWarehouse(@RequestBody Warehouse warehouse) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.saveWarehouse(warehouse)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Warehouse>> updateWarehouse(@PathVariable UUID id, @RequestBody Warehouse warehouse) {
        warehouse.setId(id);
        return ResponseEntity.ok(ApiResponse.success(warehouseService.saveWarehouse(warehouse)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Void>> deleteWarehouse(@PathVariable UUID id) {
        warehouseService.deleteWarehouse(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
