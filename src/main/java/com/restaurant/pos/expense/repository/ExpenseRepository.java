package com.restaurant.pos.expense.repository;

import com.restaurant.pos.expense.domain.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Enterprise-grade Repository for Expense entities.
 * Dedicated to the expenses table.
 */
@Repository
public interface ExpenseRepository extends JpaRepository<Expense, UUID>, JpaSpecificationExecutor<Expense> {
    List<Expense> findByClientIdAndOrgIdAndExpenseDateBetweenOrderByExpenseDateAsc(UUID clientId, UUID orgId, Instant from, Instant to);
    boolean existsByClientIdAndOrgIdAndExpenseNo(UUID clientId, UUID orgId, String expenseNo);
}

