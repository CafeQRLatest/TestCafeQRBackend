package com.restaurant.pos.expense.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@ToString
@EqualsAndHashCode
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Response payload for an expense transaction")
public class ExpenseResponse {

    @Schema(description = "Unique transaction identifier")
    private UUID id;

    @Schema(description = "System-generated reference number", example = "EXP-2024-001")
    private String referenceNumber;

    @Schema(description = "Associated category ID")
    private UUID categoryId;

    @Schema(description = "Associated category name for display", example = "Utilities")
    private String categoryName;

    @Schema(description = "ISO-8601 transaction timestamp")
    private Instant expenseDate;

    @Schema(description = "Total transaction amount", example = "1200.00")
    private BigDecimal amount;

    @Schema(description = "Transaction narrative or notes")
    private String description;

    @Schema(description = "Payment channel recorded", example = "UPI")
    private String paymentMethod;

    @Schema(description = "Operational status of the record", example = "true")
    private Boolean active;

    @Schema(description = "Organizational branch identifier")
    private UUID orgId;

    @Schema(description = "Expense scope: GLOBAL for organization-level records, BRANCH for branch records")
    private String scope;
}
