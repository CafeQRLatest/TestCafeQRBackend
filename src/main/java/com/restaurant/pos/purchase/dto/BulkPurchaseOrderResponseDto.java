package com.restaurant.pos.purchase.dto;

import com.restaurant.pos.order.dto.OrderResponseDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Result summary of bulk Purchase Order operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Result summary of bulk purchase order operations")
public class BulkPurchaseOrderResponseDto {

    @Schema(description = "Total number of requested orders")
    private int totalRequested;

    @Schema(description = "Number of successfully processed orders")
    private int processedCount;

    @Schema(description = "List of successfully processed order UUIDs")
    @Builder.Default
    private List<UUID> successfulIds = new ArrayList<>();

    @Schema(description = "List of successfully processed order DTOs")
    @Builder.Default
    private List<OrderResponseDto> processedOrders = new ArrayList<>();

    @Schema(description = "List of any errors encountered during processing")
    @Builder.Default
    private List<String> errors = new ArrayList<>();
}
