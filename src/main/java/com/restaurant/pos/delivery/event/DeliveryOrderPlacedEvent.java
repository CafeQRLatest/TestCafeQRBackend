package com.restaurant.pos.delivery.event;

import com.restaurant.pos.order.domain.Order;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class DeliveryOrderPlacedEvent extends ApplicationEvent {
    private final Order order;

    public DeliveryOrderPlacedEvent(Object source, Order order) {
        super(source);
        this.order = order;
    }
}
