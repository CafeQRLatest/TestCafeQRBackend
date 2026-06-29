package com.restaurant.pos.waste.controller;

import com.restaurant.pos.common.dto.ApiResponse;
import com.restaurant.pos.waste.domain.WasteCategory;
import com.restaurant.pos.waste.domain.WasteLog;
import com.restaurant.pos.waste.service.WasteService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/waste") @RequiredArgsConstructor
@com.restaurant.pos.subscription.annotation.RequireModule(com.restaurant.pos.subscription.domain.ModuleName.INVENTORY)
public class WasteController {
    private final WasteService wasteService;

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<Object>> getCategories() {
        return ResponseEntity.ok(ApiResponse.success(wasteService.getCategories()));
    }
    @PostMapping("/categories")
    public ResponseEntity<ApiResponse<Object>> createCategory(@RequestBody WasteCategory c) {
        return ResponseEntity.ok(ApiResponse.success(wasteService.createCategory(c)));
    }
    @PutMapping("/categories/{id}")
    public ResponseEntity<ApiResponse<Object>> updateCategory(@PathVariable UUID id, @RequestBody WasteCategory c) {
        return ResponseEntity.ok(ApiResponse.success(wasteService.updateCategory(id, c)));
    }
    @DeleteMapping("/categories/{id}")
    public ResponseEntity<ApiResponse<Object>> deleteCategory(@PathVariable UUID id) {
        wasteService.deleteCategory(id);
        return ResponseEntity.ok(ApiResponse.success("Deleted"));
    }
    @GetMapping("/logs")
    public ResponseEntity<ApiResponse<Object>> getLogs(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                wasteService.getLogs(start, end, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "wasteDate")))));
    }
    @PostMapping("/logs")
    public ResponseEntity<ApiResponse<Object>> createLog(@RequestBody WasteLog log) {
        return ResponseEntity.ok(ApiResponse.success(wasteService.createLog(log)));
    }
    @PutMapping("/logs/{id}")
    public ResponseEntity<ApiResponse<Object>> updateLog(@PathVariable UUID id, @RequestBody WasteLog log) {
        return ResponseEntity.ok(ApiResponse.success(wasteService.updateLog(id, log)));
    }
    @DeleteMapping("/logs/{id}")
    public ResponseEntity<ApiResponse<Object>> deleteLog(@PathVariable UUID id) {
        wasteService.deleteLog(id);
        return ResponseEntity.ok(ApiResponse.success("Deleted"));
    }
    @GetMapping("/analytics")
    public ResponseEntity<ApiResponse<Object>> getAnalytics(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return ResponseEntity.ok(ApiResponse.success(wasteService.getAnalytics(start, end)));
    }
}