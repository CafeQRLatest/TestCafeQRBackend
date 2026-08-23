package com.restaurant.pos.delivery.api;

import com.restaurant.pos.common.dto.ApiResponse;
import com.restaurant.pos.delivery.query.DeliveryQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CQRS Query Controller for CafeQR Delivery Web application.
 * Handles read operations (slug resolution, store settings, menu, track order, list customer orders).
 */
@Slf4j
@RestController
@RequestMapping("/api/delivery")
@RequiredArgsConstructor
@Tag(name = "Delivery Queries", description = "Public read endpoints for CafeQR delivery customer interface.")
public class DeliveryQueryController {

    private final DeliveryQueryService queryService;

    @GetMapping("/resolve")
    @Operation(summary = "Resolve Store Handle", description = "Public handle/slug & domain resolver for clean, brandable URLs.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> resolveSlug(
            @RequestParam(required = true) String handle,
            @RequestParam(required = false) String branch) {
        log.info("Resolving store handle={} branch={}", handle, branch);
        Map<String, Object> data = queryService.resolveSlug(handle, branch);
        return ResponseEntity.ok(ApiResponse.success("Resolved successfully", data));
    }

    @GetMapping("/restaurant/{clientId}/settings")
    @Operation(summary = "Get Restaurant Settings", description = "Returns brand info, logo, delivery toggles, and tax configuration.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSettings(
            @PathVariable UUID clientId,
            @RequestParam(required = false) String orgId) {
        log.info("Fetching delivery settings for clientId={} orgId={}", clientId, orgId);
        Map<String, Object> settings = queryService.getSettings(clientId, orgId);
        return ResponseEntity.ok(ApiResponse.success(settings));
    }

    @GetMapping("/restaurant/{clientId}/menu")
    @Operation(summary = "Get Delivery Menu", description = "Returns available menu items with category and variant information.")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getMenu(
            @PathVariable UUID clientId,
            @RequestParam(required = false) String orgId) {
        log.info("Fetching delivery menu for clientId={} orgId={}", clientId, orgId);
        List<Map<String, Object>> menu = queryService.getMenu(clientId, orgId);
        return ResponseEntity.ok(ApiResponse.success(menu));
    }

    @GetMapping("/orders/{orderId}")
    @Operation(summary = "Track Delivery Order", description = "Returns full order details for order tracking page.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOrder(
            @PathVariable String orderId,
            @RequestParam UUID clientId) {
        log.info("Tracking delivery orderId={} clientId={}", orderId, clientId);
        Map<String, Object> orderMap = queryService.getOrder(orderId, clientId);
        return ResponseEntity.ok(ApiResponse.success(orderMap));
    }

    @GetMapping("/orders")
    @Operation(summary = "List Customer Orders", description = "Lists orders for a customer by email and clientId.")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listOrders(
            @RequestParam UUID clientId,
            @RequestParam String email) {
        log.info("Listing customer orders for email={} clientId={}", email, clientId);
        List<Map<String, Object>> orders = queryService.listOrders(clientId, email);
        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    @GetMapping("/addresses")
    @Operation(summary = "Get Saved Delivery Addresses", description = "Returns saved delivery addresses for customer email.")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAddresses(
            @RequestParam UUID clientId,
            @RequestParam String email) {
        return ResponseEntity.ok(ApiResponse.success(Collections.emptyList()));
    }
}
