package com.restaurant.pos.expense.spec;

import com.restaurant.pos.common.util.SecurityUtils;
import com.restaurant.pos.expense.domain.Expense;
import com.restaurant.pos.expense.domain.ExpenseStatus;
import com.restaurant.pos.expense.dto.ExpenseSearchCriteria;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Enterprise-grade Specification for dynamic Expense filtering.
 * Centralizes complex query logic for consistency and testability.
 * Now optimized for the dedicated Expense domain entity.
 */
public class ExpenseSpecification {

    public static Specification<Expense> filterBy(ExpenseSearchCriteria criteria, UUID clientId, UUID orgId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Base Filters
            predicates.add(cb.equal(root.get("clientId"), clientId));
            // Note: OrderType.EXPENSE filter is handled automatically by JPA inheritance (@DiscriminatorValue)

            // Multi-Branch Security Logic
            if (!SecurityUtils.isSuperAdmin() && orgId != null) {
                predicates.add(cb.equal(root.get("orgId"), orgId));
            } else if (criteria.getBranchId() != null) {
                predicates.add(cb.equal(root.get("orgId"), criteria.getBranchId()));
            }

            // Date Range
            if (criteria.getFromDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("orderDate"), criteria.getFromDate()));
            }
            if (criteria.getToDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("orderDate"), criteria.getToDate()));
            }

            // Categorization
            if (criteria.getCategoryId() != null) {
                predicates.add(cb.equal(root.get("expenseCategoryId"), criteria.getCategoryId()));
            }

            // Payment Method — for Expense entities the payment method is stored in
            // the 'reference' column (set by ExpenseService.buildExpenseEntity).
            if (criteria.getPaymentMethod() != null && !criteria.getPaymentMethod().isBlank()) {
                predicates.add(cb.equal(root.get("reference"), criteria.getPaymentMethod()));
            }

            // Fuzzy Search (Order No or Description)
            if (criteria.getSearchTerm() != null && !criteria.getSearchTerm().isBlank()) {
                String safe = criteria.getSearchTerm()
                        .replace("%", "\\%")
                        .replace("_", "\\_")
                        .trim();
                String pattern = "%" + safe.toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("orderNo")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern)
                ));
            }

            // Status Filtering (Strictly Mutually Exclusive)
            if (criteria.getStatus() != null && ExpenseStatus.VOID.equalsIgnoreCase(criteria.getStatus())) {
                // VOID History: Show only records marked as inactive or explicitly voided
                predicates.add(cb.or(
                    cb.equal(root.get("isactive"), ExpenseStatus.INACTIVE_FLAG),
                    cb.equal(root.get("orderStatus"), ExpenseStatus.VOID)
                ));
            } else {
                // ACTIVE: Show only records that are both isactive='Y' AND not status 'VOID'
                // This is the default if status is null, empty, or "ACTIVE"
                predicates.add(cb.and(
                    cb.equal(root.get("isactive"), ExpenseStatus.ACTIVE_FLAG),
                    cb.notEqual(root.get("orderStatus"), ExpenseStatus.VOID)
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
