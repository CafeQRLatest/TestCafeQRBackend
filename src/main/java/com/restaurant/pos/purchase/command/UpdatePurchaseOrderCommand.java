package com.restaurant.pos.purchase.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * CQRS Command model for updating an existing Purchase Order.
 */
@Data
@Schema(description = "Command payload for updating an existing Purchase Order")
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public class UpdatePurchaseOrderCommand {

    @Schema(description = "Supplier/vendor UUID")
    private UUID vendorId;

    @Schema(description = "Destination warehouse UUID")
    private UUID warehouseId;

    @Schema(description = "Target order status (DRAFT, CONFIRMED, COMPLETED, CANCELLED)")
    private String orderStatus;

    @Schema(description = "Payment status (PENDING, PARTIAL, PAID)")
    private String paymentStatus;

    @Schema(description = "Payment method (CASH, CREDIT, BANK_TRANSFER, etc.)")
    private String paymentMethod;

    @Schema(description = "External purchase reference or supplier invoice number")
    private String reference;

    @Schema(description = "Internal notes or description")
    private String description;

    @Schema(description = "Pre-calculated subtotal before tax", example = "1000.00")
    private BigDecimal totalAmount;

    @Schema(description = "Pre-calculated total tax amount", example = "180.00")
    private BigDecimal totalTaxAmount;

    @Schema(description = "Pre-calculated total discount amount", example = "0.00")
    private BigDecimal totalDiscountAmount;

    @Schema(description = "Grand total payable to vendor", example = "1180.00")
    private BigDecimal grandTotal;

    @Valid
    @Schema(description = "Updated list of purchase order line items")
    private List<CreatePurchaseOrderCommand.PurchaseOrderLineCommand> lines;
}
