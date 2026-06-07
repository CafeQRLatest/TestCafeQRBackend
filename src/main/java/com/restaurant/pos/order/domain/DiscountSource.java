package com.restaurant.pos.order.domain;

/**
 * Identifies the originating source of an order-level discount.
 *
 * <p>Stored on both {@code orders} and {@code invoices} so that historical
 * discount-source reports remain accurate even after order archival.</p>
 *
 * <ul>
 *   <li>{@code MANUAL}    – cashier-entered discount at the POS counter</li>
 *   <li>{@code QR}        – discount triggered by a customer QR scan</li>
 *   <li>{@code COUPON}    – redeemed coupon / promo code</li>
 *   <li>{@code PROMOTION} – automatic promotion rule applied by the system</li>
 *   <li>{@code LOYALTY}   – loyalty points redemption</li>
 * </ul>
 *
 * The database column is {@code VARCHAR(30)} — {@link jakarta.persistence.EnumType#STRING} mapping.
 * New sources (e.g. {@code STAFF}, {@code BIRTHDAY}) can be added without schema changes.
 */
public enum DiscountSource {
    MANUAL,
    QR,
    COUPON,
    PROMOTION,
    LOYALTY
}
