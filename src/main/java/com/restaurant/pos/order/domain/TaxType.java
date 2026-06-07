package com.restaurant.pos.order.domain;

/**
 * Describes how tax is applied to an order line.
 *
 * <ul>
 *   <li>{@code INCLUSIVE} – the unit_price already includes GST (MRP / packaged goods).
 *       taxable base = face ÷ (1 + rate/100).</li>
 *   <li>{@code EXCLUSIVE} – GST is added on top of the base price.</li>
 *   <li>{@code NONE} – no tax applies to this line (zero-rated or non-taxable item).</li>
 * </ul>
 *
 * The database column is {@code VARCHAR(10)} — {@link jakarta.persistence.EnumType#STRING} mapping.
 */
public enum TaxType {
    INCLUSIVE,
    EXCLUSIVE,
    NONE
}
