package com.restaurant.pos.expense.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
@Schema(description = "Payload for updating an existing expense transaction")
public class UpdateExpenseRequest implements ExpenseBaseRequest {

    @NotNull(message = "Category ID is required")
    @Schema(description = "ID of the associated expense category")
    private UUID categoryId;

    @Schema(description = "Date and time of the expense occurrence")
    private Instant expenseDate;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be greater than zero")
    @Schema(description = "Transaction amount", example = "1500.50")
    private BigDecimal amount;

    @Schema(description = "Optional narrative description", example = "Monthly internet bill updated")
    private String description;

    @Schema(description = "Method of payment used", example = "CASH")
    private String paymentMethod;

    @Schema(description = "Target branch ID for organizational attribution")
    private UUID branchId;

    @Schema(description = "Expense scope: GLOBAL for organization-level, BRANCH for a specific branch", example = "BRANCH")
    private String scope;
}
