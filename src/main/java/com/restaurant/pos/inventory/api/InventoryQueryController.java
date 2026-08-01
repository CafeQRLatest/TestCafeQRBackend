package com.restaurant.pos.inventory.api;

import com.restaurant.pos.common.dto.ApiResponse;
import com.restaurant.pos.common.security.StaffAccess;
import com.restaurant.pos.inventory.domain.StockAdjustment;
import com.restaurant.pos.inventory.domain.StockSnapshot;
import com.restaurant.pos.inventory.domain.StockTransfer;
import com.restaurant.pos.inventory.query.InventoryQueryService;
import com.restaurant.pos.subscription.annotation.RequireModule;
import com.restaurant.pos.subscription.domain.ModuleName;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * CQRS Query Controller for Inventory operations.
 * Handles read queries (STOCK OVERVIEW, ADJUSTMENT LIST, TRANSFER LIST).
 */
@Slf4j
@StaffAccess
@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
@Validated
@RequireModule(ModuleName.INVENTORY)
@Tag(name = "Inventory Queries", description = "Endpoints for querying inventory balances and history.")
public class InventoryQueryController {

    private final InventoryQueryService queryService;

    @GetMapping("/stock-overview/{warehouseId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    @Operation(summary = "Get Warehouse Stock Overview", description = "Retrieves stock snapshots for a specific warehouse.")
    public ResponseEntity<ApiResponse<List<StockSnapshot>>> getStockOverview(@PathVariable UUID warehouseId) {
        return ResponseEntity.ok(ApiResponse.success(queryService.getStockOverview(warehouseId)));
    }

    @GetMapping("/stock-overview")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    @Operation(summary = "Get Consolidated Stock Overview", description = "Retrieves consolidated stock balances across warehouses.")
    public ResponseEntity<ApiResponse<List<StockSnapshot>>> getConsolidatedStockOverview(
            @RequestParam(required = false) UUID orgId,
            @RequestParam(required = false) UUID warehouseId) {
        return ResponseEntity.ok(ApiResponse.success(queryService.getConsolidatedStockOverview(orgId, warehouseId)));
    }

    @GetMapping("/adjustments")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER')")
    @Operation(summary = "Get Stock Adjustments", description = "Retrieves paginated stock adjustment audit records.")
    public ResponseEntity<ApiResponse<Page<StockAdjustment>>> getAdjustments(
            @RequestParam(required = false) UUID orgId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "adjustmentDate"));
        return ResponseEntity.ok(ApiResponse.success(queryService.getAdjustments(orgId, pageable)));
    }

    @GetMapping("/transfers")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER')")
    @Operation(summary = "Get Stock Transfers", description = "Retrieves paginated stock transfer records.")
    public ResponseEntity<ApiResponse<Page<StockTransfer>>> getTransfers(
            @RequestParam(required = false) UUID orgId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "transferDate"));
        return ResponseEntity.ok(ApiResponse.success(queryService.getTransfers(orgId, pageable)));
    }

    @GetMapping("/transfers/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    @Operation(summary = "Get Stock Transfer By ID", description = "Retrieves details of a specific stock transfer.")
    public ResponseEntity<ApiResponse<StockTransfer>> getTransferById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(queryService.getTransferById(id)));
    }

    @GetMapping("/adjustments/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    @Operation(summary = "Get Stock Adjustment By ID", description = "Retrieves details of a specific stock adjustment.")
    public ResponseEntity<ApiResponse<StockAdjustment>> getAdjustmentById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(queryService.getAdjustmentById(id)));
    }
}
