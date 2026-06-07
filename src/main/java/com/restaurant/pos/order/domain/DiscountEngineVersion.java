package com.restaurant.pos.order.domain;

/**
 * Version constant for the GST discount calculation engine.
 *
 * Use {@code DiscountEngineVersion.CURRENT} everywhere instead of a raw string literal.
 * When the discount algorithm changes, bump the constant to GST_ENGINE_V2 and update this
 * Javadoc. Old orders and invoices stamped with V1 continue to be readable without migration.
 */
public final class DiscountEngineVersion {

    /** The active discount calculation engine version stamped on every new order and invoice. */
    public static final String CURRENT = "GST_ENGINE_V1";

    private DiscountEngineVersion() {}
}
