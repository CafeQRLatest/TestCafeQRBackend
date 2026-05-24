package com.restaurant.pos.expense.domain;

/**
 * Standardized status constants for Expense transactions and associated documents.
 * Replaces scattered magic strings to ensure compile-time safety and ledger integrity.
 */
public enum ExpenseStatus {
    VOID, PAID, COMPLETED, PENDING;
}
