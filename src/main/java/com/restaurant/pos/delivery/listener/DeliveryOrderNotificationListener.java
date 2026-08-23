package com.restaurant.pos.delivery.listener;

import com.restaurant.pos.delivery.event.DeliveryOrderPlacedEvent;
import com.restaurant.pos.order.domain.Order;
import com.restaurant.pos.print.domain.PrintJobKind;
import com.restaurant.pos.print.service.PrintJobService;
import com.restaurant.pos.push.service.PushNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryOrderNotificationListener {

    private final PrintJobService printJobService;
    private final PushNotificationService pushNotificationService;

    @Async
    @EventListener
    public void handleDeliveryOrderPlaced(DeliveryOrderPlacedEvent event) {
        Order order = event.getOrder();
        if (order == null) return;

        log.info("Processing asynchronous notification tasks for delivery order: {}", order.getOrderNo());

        // 1. Queue Print Job for POS / KDS
        try {
            printJobService.enqueueForOrder(order, PrintJobKind.KOT, "auto");
        } catch (Exception e) {
            log.warn("Failed to queue print job for delivery order {}: {}", order.getId(), e.getMessage());
        }

        // 2. Trigger FCM Push Notification
        try {
            pushNotificationService.sendNewOrderPush(order);
        } catch (Exception e) {
            log.warn("Failed to send push notification for delivery order {}: {}", order.getId(), e.getMessage());
        }
    }
}
