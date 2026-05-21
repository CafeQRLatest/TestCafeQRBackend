package com.restaurant.pos.expense.repository;

import com.restaurant.pos.expense.domain.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
    @Query("""
            SELECT e FROM Expense e
            WHERE e.clientId = :clientId
              AND (:orgId IS NULL OR e.orgId = :orgId)
              AND e.expenseDate BETWEEN :from AND :to
            ORDER BY e.expenseDate ASC
            """)
    List<Expense> findByClientIdAndOrgIdAndExpenseDateBetweenOrderByExpenseDateAsc(@Param("clientId") UUID clientId, @Param("orgId") UUID orgId, @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            SELECT COUNT(e) > 0 FROM Expense e
            WHERE e.clientId = :clientId
              AND ((:orgId IS NULL AND e.orgId IS NULL) OR e.orgId = :orgId)
              AND e.expenseNo = :expenseNo
            """)
    boolean existsByClientIdAndOrgIdAndExpenseNo(@Param("clientId") UUID clientId, @Param("orgId") UUID orgId, @Param("expenseNo") String expenseNo);
}

