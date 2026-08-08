package com.restaurant.pos.purchase.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Command payload for bulk Purchase Order actions (bulk-void, bulk-receive).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload for bulk Purchase Order operations")
public class BulkPurchaseOrderActionCommand {

    @NotEmpty(message = "Order IDs must not be empty")
    @Schema(description = "List of Purchase Order UUIDs to process", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<UUID> orderIds;
}
