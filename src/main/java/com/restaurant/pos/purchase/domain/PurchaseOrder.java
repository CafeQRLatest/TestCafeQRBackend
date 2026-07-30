package com.restaurant.pos.purchase.domain;

import com.restaurant.pos.common.exception.BusinessException;
import com.restaurant.pos.order.domain.Order;
import com.restaurant.pos.order.domain.OrderStatus;
import com.restaurant.pos.order.domain.OrderType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Domain Entity for Purchase Orders (Money Flow OUT).
 * Enforces domain state transitions (complete, confirm, cancel).
 */
@Entity
@Getter
@Setter
@ToString(callSuper = true)
@DiscriminatorValue("PURCHASE")
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public class PurchaseOrder extends Order {

    public PurchaseOrder() {
        super();
        this.setOrderType(OrderType.PURCHASE);
    }

    // ─────────────────────────────────────────────────────────────
    // Domain State Transition Methods
    // ─────────────────────────────────────────────────────────────

    /**
     * Advances the Purchase Order status to CONFIRMED.
     */
    public void confirm() {
        if ("CANCELLED".equalsIgnoreCase(this.getOrderStatus()) || "VOID".equalsIgnoreCase(this.getOrderStatus())) {
            throw new BusinessException("Cannot confirm a cancelled or void Purchase Order");
        }
        this.setOrderStatus(OrderStatus.CONFIRMED.name());
    }

    /**
     * Advances the Purchase Order status to COMPLETED.
     */
    public void complete() {
        if ("CANCELLED".equalsIgnoreCase(this.getOrderStatus()) || "VOID".equalsIgnoreCase(this.getOrderStatus())) {
            throw new BusinessException("Cannot complete a cancelled or void Purchase Order");
        }
        this.setOrderStatus(OrderStatus.COMPLETED.name());
    }

    /**
     * Cancels the Purchase Order.
     */
    public void cancel() {
        if ("COMPLETED".equalsIgnoreCase(this.getOrderStatus())) {
            throw new BusinessException("Cannot cancel an already completed Purchase Order");
        }
        this.setOrderStatus(OrderStatus.CANCELLED.name());
    }

    public boolean isCompleted() {
        return OrderStatus.COMPLETED.name().equalsIgnoreCase(this.getOrderStatus());
    }
}
