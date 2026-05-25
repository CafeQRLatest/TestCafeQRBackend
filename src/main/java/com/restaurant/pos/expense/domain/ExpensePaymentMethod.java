package com.restaurant.pos.expense.domain;

import java.util.Locale;

/**
 * Standardized payment method constants for Expense transactions.
 * Collapses magic strings and provides robust string normalization.
 */
public enum ExpensePaymentMethod {
    CASH, ONLINE, UPI, CARD, BANK, CHEQUE, MIXED;

    public static ExpensePaymentMethod fromString(String value) {
        if (value == null || value.isBlank()) {
            return CASH;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
        } catch (IllegalArgumentException e) {
            return CASH;
        }
    }
}
