package com.restaurant.pos.purchase.query;

import com.restaurant.pos.order.domain.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;
import java.util.UUID;

/**
 * Encapsulated strongly-typed search query request model for Purchase Orders.
 * Lives in the 'query' package of the purchase module.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Filter and search criteria for querying Purchase Orders")
public class PurchaseOrderSearchRequest {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Schema(description = "Start date range filter (ISO-8601 UTC Instant)", example = "2026-05-24T00:00:00Z")
    private Instant fromDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Schema(description = "End date range filter (ISO-8601 UTC Instant)", example = "2026-05-24T23:59:59Z")
    private Instant toDate;

    @Schema(description = "Strongly typed order status filter (DRAFT, CONFIRMED, COMPLETED, CANCELLED)")
    private OrderStatus status;

    @Schema(description = "Filter by branch/organization UUID")
    private UUID branchId;

    @Schema(description = "Filter by supplier/vendor UUID")
    private UUID vendorId;

    @Schema(description = "Filter by storage/warehouse UUID")
    private UUID warehouseId;

    @Schema(description = "Filter by payment method (CASH, CREDIT, BANK_TRANSFER, etc.)")
    private String paymentMethod;

    @Schema(description = "Generic search query matching order number or external reference")
    private String searchTerm;
}
