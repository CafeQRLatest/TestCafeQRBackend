package com.restaurant.pos.purchasing.domain;

import com.restaurant.pos.order.domain.Order;
import com.restaurant.pos.order.domain.OrderType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Domain Entity for Purchase Orders (Money Flow OUT).
 * Moved to Purchasing module for better Domain-Driven Design.
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
}
