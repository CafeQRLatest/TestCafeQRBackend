package com.restaurant.pos.expense.mapper;

import com.restaurant.pos.expense.domain.Expense;
import com.restaurant.pos.expense.dto.ExpenseResponse;
import org.springframework.stereotype.Component;

/**
 * Maps between Expense entities and their DTOs.
 */
@Component
public class ExpenseMapper {

    public ExpenseResponse toExpenseResponse(Expense expense, String categoryName) {
        
        String method = (expense.getPaymentMethod() != null && !expense.getPaymentMethod().isBlank()) 
                ? expense.getPaymentMethod() 
                : "CASH";

        return ExpenseResponse.builder()
                .id(expense.getId())
                .referenceNumber(expense.getExpenseNo())
                .categoryId(expense.getCategoryId())
                .categoryName(categoryName)
                .expenseDate(expense.getExpenseDate())
                .amount(expense.getAmount())
                .description(expense.getDescription())
                .paymentMethod(method)
                .active("Y".equals(expense.getIsactive()))
                .orgId(expense.getOrgId())
                .scope(expense.getOrgId() == null ? "GLOBAL" : "BRANCH")
                .updatedBy(expense.getUpdatedBy())
                .build();
    }

    public ExpenseResponse toExpenseResponse(Expense expense, String categoryName, String updatedByName) {
        ExpenseResponse response = toExpenseResponse(expense, categoryName);
        if (updatedByName != null) {
            return response.toBuilder()
                    .updatedBy(updatedByName)
                    .build();
        }
        return response;
    }
}
