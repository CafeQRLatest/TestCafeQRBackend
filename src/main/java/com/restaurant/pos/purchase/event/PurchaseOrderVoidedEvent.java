package com.restaurant.pos.purchase.event;

import com.restaurant.pos.order.domain.Order;

/**
 * Domain Event fired when a Purchase Order is voided or when an existing completed
 * Purchase Order is modified, requiring prior invoices, payments, and stock intake to be voided/reversed.
 */
public record PurchaseOrderVoidedEvent(Order purchaseOrder) {
}
