package com.restaurant.pos.paymenttype.api;

import com.restaurant.pos.common.dto.ApiResponse;
import com.restaurant.pos.common.security.StaffAccess;
import com.restaurant.pos.paymenttype.domain.PaymentType;
import com.restaurant.pos.paymenttype.query.PaymentTypeQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * CQRS Query Controller for PaymentType master data.
 * Handles read operations: LIST (with optional context filter).
 */
@Slf4j
@StaffAccess
@RestController
@RequestMapping("/api/v1/payment-types")
@RequiredArgsConstructor
@Validated
@Tag(name = "Payment Type Queries", description = "Endpoints for querying Payment Type master data.")
public class PaymentTypeQueryController {

    private final PaymentTypeQueryService queryService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    @Operation(
        summary = "List Payment Types",
        description = "Returns all payment types for the current branch. " +
                      "Use 'applicableFor' to filter by context: SALES, PURCHASES, or EXPENSES."
    )
    public ResponseEntity<ApiResponse<List<PaymentType>>> getPaymentTypes(
            @Parameter(description = "Filter context: SALES, PURCHASES, EXPENSES")
            @RequestParam(required = false) String applicableFor,
            @Parameter(description = "Branch override (orgId). Defaults to branch from security context.")
            @RequestParam(required = false) UUID orgId) {

        log.info("Listing payment types | applicableFor={} | orgId={}", applicableFor, orgId);
        List<PaymentType> result = (applicableFor != null && !applicableFor.isBlank())
                ? queryService.getPaymentTypesByApplicableFor(applicableFor, orgId)
                : queryService.getPaymentTypes(orgId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}