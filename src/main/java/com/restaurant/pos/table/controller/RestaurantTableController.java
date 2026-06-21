package com.restaurant.pos.table.controller;

import com.restaurant.pos.common.dto.ApiResponse;
import com.restaurant.pos.table.domain.RestaurantTable;
import com.restaurant.pos.table.service.RestaurantTableService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tables")
@RequiredArgsConstructor
public class RestaurantTableController {

    private final RestaurantTableService tableService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<RestaurantTable>>> getTables() {
        return ResponseEntity.ok(ApiResponse.success(tableService.getAllTables()));
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<RestaurantTable>>> getActiveTables() {
        return ResponseEntity.ok(ApiResponse.success(tableService.getActiveTables()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<RestaurantTable>> getTable(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(tableService.getTable(id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<RestaurantTable>> createTable(@RequestBody RestaurantTable table) {
        return ResponseEntity.ok(ApiResponse.success(tableService.saveTable(table)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<RestaurantTable>> updateTable(@PathVariable UUID id, @RequestBody RestaurantTable table) {
        table.setId(id);
        return ResponseEntity.ok(ApiResponse.success(tableService.saveTable(table)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<RestaurantTable>> updateStatus(@PathVariable UUID id, @RequestParam String status) {
        return ResponseEntity.ok(ApiResponse.success(tableService.updateTableStatus(id, status)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Void>> deleteTable(@PathVariable UUID id) {
        tableService.deleteTable(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{id}/send-qr")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Void>> sendQRCode(
            @PathVariable UUID id, 
            @RequestParam(required = false) String email, 
            @RequestParam String qrLink) {
        tableService.sendQRCode(id, email, qrLink);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
