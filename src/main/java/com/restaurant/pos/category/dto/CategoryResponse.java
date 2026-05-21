package com.restaurant.pos.category.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Enterprise-grade Response DTO for Expense Categories.
 */
@Getter
@ToString
@EqualsAndHashCode
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Expense category response payload")
public class CategoryResponse {

    @Schema(description = "Unique identifier of the category", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;

    @Schema(description = "Name of the category used for classification", example = "Utilities")
    private String name;

    @Schema(description = "Display sort order for UI positioning", example = "10")
    private Integer sortOrder;

    @Schema(description = "Indicates if the category is currently available for operational use", example = "true")
    private Boolean active;

    @Schema(description = "Branch ID for branch categories; null for organization categories")
    private UUID orgId;

    @Schema(description = "Category scope: GLOBAL or BRANCH")
    private String scope;

    @Schema(description = "ISO-8601 timestamp of record creation")
    private LocalDateTime createdAt;

    @Schema(description = "ISO-8601 timestamp of the last record update")
    private LocalDateTime updatedAt;

    /**
     * Minimalist view for high-performance dropdown lists.
     */
    @Getter
    @ToString
    @EqualsAndHashCode
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Lightweight view of an expense category")
    public static class SimpleCategory {
        @Schema(description = "Identifier")
        private UUID id;
        
        @Schema(description = "Display Name")
        private String name;
    }
}
