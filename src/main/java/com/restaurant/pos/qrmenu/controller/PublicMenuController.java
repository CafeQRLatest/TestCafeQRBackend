package com.restaurant.pos.qrmenu.controller;

import com.restaurant.pos.client.repository.ClientRepository;
import com.restaurant.pos.client.repository.OrganizationRepository;
import com.restaurant.pos.common.dto.ApiResponse;
import com.restaurant.pos.common.exception.BusinessException;
import com.restaurant.pos.common.service.SystemConfigurationService;
import com.restaurant.pos.product.domain.Product;
import com.restaurant.pos.product.repository.ProductRepository;
import com.restaurant.pos.qrmenu.service.QrTableSessionService;
import com.restaurant.pos.subscription.domain.ModuleName;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Public (unauthenticated) REST Controller for QR Code Scanning & Ordering.
 * Supports both raw UUIDs and human-readable Slugs (e.g. /menu/cafe-delight/downtown/table-5).
 */
@RestController
@RequestMapping("/api/v1/public/menu")
@RequiredArgsConstructor
public class PublicMenuController {

    private final ProductRepository productRepository;
    private final OrganizationRepository organizationRepository;
    private final ClientRepository clientRepository;
    private final SystemConfigurationService systemConfigurationService;
    private final QrTableSessionService qrTableSessionService;

    /**
     * GET /api/v1/public/menu/{clientId}/{orgId}
     * Returns the full active menu for a given restaurant (client + org, via UUID or slug).
     */
    @GetMapping("/{clientId}/{orgId}")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getMenu(
            @PathVariable String clientId,
            @PathVariable String orgId) {
        UUID clientUuid = qrTableSessionService.resolveClientId(clientId);
        UUID orgUuid = qrTableSessionService.resolveOrgId(clientUuid, orgId);

        validateSubscription(clientUuid, orgUuid);
        List<Product> products = productRepository
                .findByClientIdAndOrgIdOrGlobalAndIsActiveTrue(clientUuid, orgUuid);

        List<Map<String, Object>> menu = products.stream()
                .filter(Product::isAvailable)
                .map(p -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", p.getId());
                    item.put("name", p.getName());
                    item.put("description", p.getDescription());
                    item.put("price", p.getPrice());
                    item.put("imageUrl", p.getImageUrl());
                    item.put("category", p.getCategory() != null ? p.getCategory().getName() : "Others");
                    item.put("isVeg", !p.isPackagedGood());
                    item.put("isAvailable", p.isAvailable());
                    item.put("productType", p.getProductType());
                    return item;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(menu));
    }

    /**
     * GET /api/v1/public/menu/{clientId}/{orgId}/table/{tableId}
     * Returns table info & active open session for the scanned QR code (via UUID or slug).
     */
    @GetMapping("/{clientId}/{orgId}/table/{tableId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTableInfo(
            @PathVariable String clientId,
            @PathVariable String orgId,
            @PathVariable String tableId) {
        Map<String, Object> info = qrTableSessionService.getTableSessionInfo(clientId, orgId, tableId);
        return ResponseEntity.ok(ApiResponse.success(info));
    }

    /**
     * GET /api/v1/public/menu/{clientId}/default-org
     * Helper endpoint for legacy QR code redirects. Returns the default orgId for a given clientId/slug.
     */
    @GetMapping("/{clientId}/default-org")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDefaultOrg(@PathVariable String clientId) {
        UUID clientUuid = qrTableSessionService.resolveClientId(clientId);
        List<com.restaurant.pos.client.domain.Organization> orgs = organizationRepository.findAllByClientId(clientUuid);

        if (orgs == null || orgs.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success(Map.of("orgId", clientUuid)));
        }

        com.restaurant.pos.client.domain.Organization defaultOrg = orgs.stream()
                .filter(o -> "HQ".equals(o.getBranchCode()))
                .findFirst()
                .orElse(orgs.get(0));

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "orgId", defaultOrg.getId(),
                "slug", defaultOrg.getSlug() != null ? defaultOrg.getSlug() : defaultOrg.getBranchCode()
        )));
    }

    /**
     * POST /api/v1/public/menu/{clientId}/{orgId}/order
     * Places or appends an order from the QR menu (via UUID or slug).
     */
    @PostMapping("/{clientId}/{orgId}/order")
    public ResponseEntity<ApiResponse<Map<String, Object>>> placeOrder(
            @PathVariable String clientId,
            @PathVariable String orgId,
            @RequestBody Map<String, Object> payload) {
        Map<String, Object> response = qrTableSessionService.processOrder(clientId, orgId, payload);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private void validateSubscription(UUID clientId, UUID orgId) {
        if (clientId != null) {
            com.restaurant.pos.client.domain.Client client = clientRepository.findById(clientId).orElse(null);
            if (client == null || !client.isSubscriptionActive()) {
                throw new BusinessException("The restaurant's subscription has expired. Online menu and ordering are currently unavailable.");
            }

            boolean tableQrActive = false;
            String status = client.getSubscriptionStatus();
            if (status != null && "TRIAL".equalsIgnoreCase(status.trim()) && client.getSubscriptionExpiryDate() != null && client.getSubscriptionExpiryDate().isAfter(java.time.LocalDateTime.now())) {
                tableQrActive = true;
            } else {
                tableQrActive = systemConfigurationService.isModuleActive(clientId, orgId, ModuleName.TABLE_QR);
            }

            if (!tableQrActive) {
                throw new BusinessException("Online Ordering is not active for this branch. Please contact restaurant administration.");
            }
        }
    }
}
