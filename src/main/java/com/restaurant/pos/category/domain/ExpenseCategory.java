package com.restaurant.pos.category.domain;

import com.restaurant.pos.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Enterprise-grade Entity for Expense Classification.
 * Maintains compatibility with project-wide "Y/N" status conventions 
 * while providing rich domain behavior methods.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
    name = "expense_categories",
    indexes = {
        @Index(name = "idx_expense_category_client", columnList = "client_id"),
        @Index(name = "idx_expense_category_org", columnList = "org_id"),
        @Index(name = "idx_expense_category_owner", columnList = "client_id, org_id, created_by"),
        @Index(name = "idx_expense_category_active", columnList = "is_active"),
        @Index(name = "idx_expense_category_sort", columnList = "sort_order")
    }
)
@EqualsAndHashCode(callSuper = true)
public class ExpenseCategory extends BaseEntity {

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "is_active", nullable = false, length = 1)
    @Builder.Default
    private String isactive = "Y";

    /*
     * ── Domain Behavior Methods ─────────────────────────────────────────────
     */

    public void deactivate() {
        this.isactive = "N";
    }

    public void activate() {
        this.isactive = "Y";
    }

    public boolean isActive() {
        return "Y".equalsIgnoreCase(this.isactive);
    }

    public void updateName(String name) {
        if (name != null && !name.isBlank()) {
            this.name = name.trim();
        }
    }

    public void updateSortOrder(Integer sortOrder) {
        if (sortOrder != null) {
            this.sortOrder = sortOrder;
        }
    }
}
