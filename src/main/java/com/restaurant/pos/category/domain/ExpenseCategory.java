package com.restaurant.pos.category.domain;

import com.restaurant.pos.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import com.restaurant.pos.expense.domain.ScopeType;

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
        @Index(name = "idx_expense_category_org_sort", columnList = "client_id, org_id, sort_order")
    }
)
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class ExpenseCategory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "is_active", nullable = false, length = 1)
    @Builder.Default
    private String isActive = "Y";

    /*
     * ── Domain Behavior Methods ─────────────────────────────────────────────
     */

    public void deactivate() {
        this.isActive = "N";
    }

    public void activate() {
        this.isActive = "Y";
    }

    public boolean isActive() {
        return "Y".equalsIgnoreCase(this.isActive);
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

    public String getScope() {
        return this.getOrgId() == null 
                ? ScopeType.GLOBAL.name() 
                : ScopeType.BRANCH.name();
    }
}
