package com.restaurant.pos.expense.domain;

import java.util.Locale;

/**
 * Standard enum defining valid operational scopes for expense categories and transaction records.
 */
public enum ScopeType {
    ALL, GLOBAL, BRANCH;

    public static ScopeType from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid scope: " + value, e);
        }
    }
}
