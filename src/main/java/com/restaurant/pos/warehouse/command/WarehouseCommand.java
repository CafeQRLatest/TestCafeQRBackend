package com.restaurant.pos.warehouse.command;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Command DTO for creating or updating a Warehouse.
 * Keeps the domain entity off the public API surface.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Request payload for creating or updating a Warehouse")
public class WarehouseCommand {

    @Schema(description = "Warehouse display name", example = "Main Store", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "Short warehouse code", example = "WH-001")
    private String code;

    @Schema(description = "Physical address of the warehouse")
    private String address;

    @Schema(description = "Name of the warehouse manager")
    private String managerName;

    @Schema(description = "Phone number of the warehouse manager")
    private String managerPhone;

    @JsonProperty("isDefault")
    @Schema(description = "Whether this is the default warehouse for the branch", example = "false")
    private boolean isDefault;

    @JsonProperty("isActive")
    @Schema(description = "Active status flag (Y / N)", example = "Y")
    private String isactive = "Y";
}
