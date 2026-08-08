package com.restaurant.pos.purchase.api;

import com.restaurant.pos.common.dto.ApiResponse;
import com.restaurant.pos.common.security.StaffAccess;
import com.restaurant.pos.order.dto.OrderResponseDto;
import com.restaurant.pos.purchase.command.BulkPurchaseOrderActionCommand;
import com.restaurant.pos.purchase.command.CreatePurchaseOrderCommand;
import com.restaurant.pos.purchase.command.PurchaseOrderCommandService;
import com.restaurant.pos.purchase.command.UpdatePurchaseOrderCommand;
import com.restaurant.pos.subscription.annotation.RequireModule;
import com.restaurant.pos.subscription.domain.ModuleName;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * CQRS Command Controller for Purchase Orders.
 * Handles state mutations (CREATE, UPDATE).
 * Lives in feature-based package 'purchase.api'.
 */
@Slf4j
@StaffAccess
@RestController
@RequestMapping("/api/v1/purchase/orders")
@RequiredArgsConstructor
@Validated
@RequireModule(ModuleName.INVENTORY)
@Tag(name = "Purchase Order Commands", description = "Endpoints for creating and mutating Purchase Orders.")
public class PurchaseOrderCommandController {

    private final PurchaseOrderCommandService commandService;

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF') or hasAuthority('ORDER_WRITE')")
    @Operation(summary = "Create Purchase Order", description = "Creates a new Purchase Order document. Supports Idempotency-Key header.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Purchase Order successfully created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request payload or constraint violation"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized access", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden access", content = @Content)
    })
    public ResponseEntity<ApiResponse<OrderResponseDto>> createPurchaseOrder(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Parameter(description = "Purchase Order details including vendor, warehouse, lines, and totals", required = true)
            @Valid @RequestBody CreatePurchaseOrderCommand command) {

        log.info("Creating Purchase Order | vendorId={} | warehouseId={} | idempotencyKey={}",
                command.getVendorId(), command.getWarehouseId(), idempotencyKey);

        OrderResponseDto response = commandService.createPurchaseOrder(command, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF') or hasAuthority('ORDER_WRITE')")
    @Operation(summary = "Update Purchase Order", description = "Updates a Purchase Order draft or advances status (DRAFT → CONFIRMED → COMPLETED / CANCELLED).")
    public ResponseEntity<ApiResponse<OrderResponseDto>> updatePurchaseOrder(
            @Parameter(description = "UUID of the Purchase Order to update", required = true) @PathVariable UUID id,
            @Parameter(description = "Update payload", required = true) @Valid @RequestBody UpdatePurchaseOrderCommand command) {

        log.info("Updating Purchase Order | id={} | targetStatus={}", id, command.getOrderStatus());

        OrderResponseDto response = commandService.updatePurchaseOrder(id, command);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF') or hasAuthority('ORDER_WRITE')")
    @Operation(summary = "Void Purchase Order", description = "Voids / cancels a Purchase Order.")
    public ResponseEntity<ApiResponse<OrderResponseDto>> voidPurchaseOrder(
            @Parameter(description = "UUID of the Purchase Order to void", required = true) @PathVariable UUID id) {

        log.info("Voiding Purchase Order | id={}", id);

        OrderResponseDto response = commandService.voidPurchaseOrder(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF') or hasAuthority('ORDER_WRITE')")
    @Operation(summary = "Receive Purchase Order", description = "Marks a Purchase Order as received/completed, updating inventory and creating vendor bill.")
    public ResponseEntity<ApiResponse<OrderResponseDto>> receivePurchaseOrder(
            @Parameter(description = "UUID of the Purchase Order to receive", required = true) @PathVariable UUID id) {

        log.info("Receiving Purchase Order | id={}", id);

        OrderResponseDto response = commandService.receivePurchaseOrder(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/bulk-void")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF') or hasAuthority('ORDER_WRITE')")
    @Operation(summary = "Bulk Void Purchase Orders", description = "Voids / cancels multiple Purchase Orders in a single call.")
    public ResponseEntity<ApiResponse<com.restaurant.pos.purchase.dto.BulkPurchaseOrderResponseDto>> bulkVoidPurchaseOrders(
            @Parameter(description = "Bulk void command with order IDs", required = true) @Valid @RequestBody BulkPurchaseOrderActionCommand command) {

        log.info("Bulk voiding Purchase Orders | count={}", command.getOrderIds() != null ? command.getOrderIds().size() : 0);

        com.restaurant.pos.purchase.dto.BulkPurchaseOrderResponseDto response = commandService.bulkVoidPurchaseOrders(command.getOrderIds());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/bulk-receive")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF') or hasAuthority('ORDER_WRITE')")
    @Operation(summary = "Bulk Receive Purchase Orders", description = "Receives/completes multiple Purchase Orders in a single call.")
    public ResponseEntity<ApiResponse<com.restaurant.pos.purchase.dto.BulkPurchaseOrderResponseDto>> bulkReceivePurchaseOrders(
            @Parameter(description = "Bulk receive command with order IDs", required = true) @Valid @RequestBody BulkPurchaseOrderActionCommand command) {

        log.info("Bulk receiving Purchase Orders | count={}", command.getOrderIds() != null ? command.getOrderIds().size() : 0);

        com.restaurant.pos.purchase.dto.BulkPurchaseOrderResponseDto response = commandService.bulkReceivePurchaseOrders(command.getOrderIds());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
