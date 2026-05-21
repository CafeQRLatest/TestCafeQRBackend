package com.restaurant.pos.category.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

import java.util.UUID;

/**
 * Enterprise-grade Request DTO for Category Creation.
 */
@Getter
@ToString
@EqualsAndHashCode
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Payload for creating a new expense category")
public class CreateCategoryRequest {

    @NotBlank(message = "Category name is required")
    @Size(max = 100, message = "Category name must not exceed 100 characters")
    @Schema(description = "Name of the category", example = "Kitchen Supplies")
    private String name;

    @Min(value = 0, message = "Sort order must be non-negative")
    @Schema(description = "Display sort order for UI positioning", example = "1")
    @Builder.Default
    private Integer sortOrder = 0;

    @Schema(description = "Category scope: GLOBAL for organization-level, BRANCH for a specific branch", example = "BRANCH")
    private String scope;

    @Schema(description = "Branch ID when scope is BRANCH")
    private UUID branchId;
}
