package com.restaurant.pos.expense.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Receipt confirming the successful voiding of an expense and its associated financial chain")
public class VoidExpenseResponse {

    @Schema(description = "ID of the voided expense (Order)")
    private UUID expenseId;

    @Schema(description = "ID of the voided invoice")
    private UUID invoiceId;

    @Schema(description = "List of voided payment IDs")
    private List<UUID> paymentIds;

    @Schema(description = "UTC timestamp when the voiding was finalized")
    private Instant voidedAt;

    @Schema(description = "Audit message detailing the cascade result")
    private String message;
}
