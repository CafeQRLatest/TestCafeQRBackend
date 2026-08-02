package com.restaurant.pos.paymenttype.command;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Command DTO for creating or updating a PaymentType.
 * Keeps the domain entity off the public API surface.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Request payload for creating or updating a Payment Type")
public class PaymentTypeCommand {

    @Schema(description = "User-facing label, e.g. 'Cash', 'UPI - GPay'", requiredMode = Schema.RequiredMode.REQUIRED)
    private String displayName;

    @Schema(description = "System-level type code: CASH, ONLINE, UPI, CARD, BANK, CHEQUE, OTHERS", example = "CASH")
    private String paymentType = "OTHERS";

    @JsonProperty("sales")
    @Schema(description = "Available for Sales (Y/N)", example = "Y")
    private String sales = "Y";

    @JsonProperty("purchase")
    @Schema(description = "Available for Purchases (Y/N)", example = "Y")
    private String purchase = "Y";

    @JsonProperty("expense")
    @Schema(description = "Available for Expenses (Y/N)", example = "Y")
    private String expense = "Y";

    @Schema(description = "Accounting GL reference code", example = "CASH_ACCT_001")
    private String ledgerRef;

    @JsonProperty("isDefault")
    @Schema(description = "Whether this is the default payment type", example = "false")
    private Boolean isDefault = false;

    @Schema(description = "Display order — lower appears first", example = "1")
    private Integer sortOrder = 0;

    @Schema(description = "Optional notes about this payment type")
    private String description;

    @JsonProperty("isActive")
    @Schema(description = "Active flag (Y/N)", example = "Y")
    private String isactive = "Y";
}