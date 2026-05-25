package com.restaurant.pos.category.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

/**
 * Enterprise-grade Request DTO for Category Updates.
 */
@Getter
@ToString
@EqualsAndHashCode
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payload for updating an existing expense category")
public class UpdateCategoryRequest {

    @NotBlank(message = "Category name is required")
    @Size(max = 100, message = "Category name must not exceed 100 characters")
    @Schema(description = "Name of the category", example = "Maintenance")
    private String name;

    @Min(value = 0, message = "Sort order must be non-negative")
    @Schema(description = "Display sort order for UI positioning", example = "5")
    private Integer sortOrder;

    @Schema(description = "Active status of the category", example = "true")
    private Boolean active;
}
