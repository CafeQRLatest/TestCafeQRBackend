package com.restaurant.pos.purchase.event;

import com.restaurant.pos.order.domain.Order;

/**
 * Domain Event fired when a Purchase Order transitions to COMPLETED status.
 * Listened to by decoupled inventory and invoice listeners.
 */
public record PurchaseOrderCompletedEvent(Order purchaseOrder) {
}
