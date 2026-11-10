package com.restaurant.pos.costing.api;

import com.restaurant.pos.common.dto.ApiResponse;
import com.restaurant.pos.common.security.StaffAccess;
import com.restaurant.pos.common.tenant.TenantContext;
import com.restaurant.pos.costing.dto.ProductCostDto;
import com.restaurant.pos.costing.query.CostingQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Read-only inventory costing endpoints: current cost of a product in the current branch.
 */
@StaffAccess
@RestController
@RequestMapping("/api/v1/costing")
@RequiredArgsConstructor
@Tag(name = "Inventory Costing", description = "Current stock cost per product (weighted average / last PO / standard).")
public class CostingQueryController {

    private final CostingQueryService costingQueryService;

    @GetMapping("/products/{productId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    @Operation(summary = "Get product cost", description = "Current cost records (base product and variants) for the current branch.")
    public ResponseEntity<ApiResponse<List<ProductCostDto>>> getProductCost(
            @PathVariable UUID productId,
            @RequestParam(value = "orgId", required = false) UUID orgId) {
        UUID clientId = TenantContext.getCurrentTenant();
        UUID effectiveOrgId = orgId != null ? orgId : TenantContext.getCurrentOrg();
        return ResponseEntity.ok(ApiResponse.success(
                costingQueryService.productCosts(clientId, effectiveOrgId, productId)));
    }
}
