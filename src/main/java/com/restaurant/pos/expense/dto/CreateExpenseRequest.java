package com.restaurant.pos.expense.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
@Schema(description = "Payload for recording a new expense transaction")
public class CreateExpenseRequest implements ExpenseBaseRequest {

    @NotNull(message = "Category ID is required")
    @Schema(description = "ID of the associated expense category")
    private UUID categoryId;

    @Schema(description = "Date and time of the expense occurrence")
    private Instant expenseDate;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be greater than zero")
    @Schema(description = "Transaction amount", example = "1500.50")
    private BigDecimal amount;

    @Schema(description = "Optional narrative description", example = "Monthly internet bill")
    private String description;

    @Pattern(regexp = "CASH|ONLINE|UPI|CARD|BANK|CHEQUE|MIXED", message = "Invalid payment method")
    @Schema(description = "Method of payment used", example = "CASH")
    private String paymentMethod;

    @Schema(description = "Target branch ID for organizational attribution")
    private UUID branchId;

    @Pattern(regexp = "GLOBAL|BRANCH", message = "Scope must be GLOBAL or BRANCH")
    @Schema(description = "Expense scope: GLOBAL for organization-level, BRANCH for a specific branch", example = "BRANCH")
    private String scope;

    @Schema(description = "Cash amount for mixed payment", example = "500.00")
    private BigDecimal cashAmount;

    @Schema(description = "Online amount for mixed payment", example = "1000.00")
    private BigDecimal onlineAmount;
}
