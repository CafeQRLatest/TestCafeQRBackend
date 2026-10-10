package com.restaurant.pos.delivery.api;

import com.restaurant.pos.order.domain.event.OrderStatusUpdatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@RestController
@RequestMapping("/api/delivery/orders")
public class OrderStatusSseController {

    private static final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private static final int MAX_ORDER_STREAMS = 5_000;
    private static final int MAX_STREAMS_PER_ORDER = 5;
    private static final java.util.concurrent.atomic.AtomicInteger openStreams = new java.util.concurrent.atomic.AtomicInteger();

    @GetMapping(value = "/{orderId}/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.http.ResponseEntity<SseEmitter> subscribeOrderStatus(@PathVariable UUID orderId) {
        List<SseEmitter> existing = emitters.get(orderId);
        if (openStreams.get() >= MAX_ORDER_STREAMS || (existing != null && existing.size() >= MAX_STREAMS_PER_ORDER)) {
            return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS)
                    .header(org.springframework.http.HttpHeaders.RETRY_AFTER, "30").build();
        }
        SseEmitter emitter = new SseEmitter(3600000L); // 1 hour timeout
        emitters.computeIfAbsent(orderId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        openStreams.incrementAndGet();

        emitter.onCompletion(() -> removeEmitter(orderId, emitter));
        emitter.onTimeout(() -> removeEmitter(orderId, emitter));
        emitter.onError((e) -> removeEmitter(orderId, emitter));

        try {
            emitter.send(SseEmitter.event().name("init").data("connected"));
        } catch (IOException e) {
            removeEmitter(orderId, emitter);
        }

        return org.springframework.http.ResponseEntity.ok(emitter);
    }

    public static void publishStatusUpdate(UUID orderId, Object status) {
        if (status == null) {
            publishStatusUpdate(orderId, (String) null);
        } else {
            publishStatusUpdate(orderId, status.toString());
        }
    }

    public static void publishStatusUpdate(UUID orderId, String status) {
        if (orderId == null) return;
        List<SseEmitter> list = emitters.get(orderId);
        if (list != null && !list.isEmpty()) {
            List<SseEmitter> deadEmitters = new ArrayList<>();
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event().name("status-update").data(status != null ? status : ""));
                } catch (Exception e) {
                    deadEmitters.add(emitter);
                }
            }
            for (SseEmitter dead : deadEmitters) {
                if (list.remove(dead)) {
                    openStreams.decrementAndGet();
                }
            }
        }
    }

    private static void removeEmitter(UUID orderId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(orderId);
        if (list != null) {
            if (list.remove(emitter)) {
                openStreams.decrementAndGet();
            }
            if (list.isEmpty()) {
                emitters.remove(orderId);
            }
        }
    }

    /**
     * Listens for {@link OrderStatusUpdatedEvent} published by the core notification module.
     * This decouples the delivery SSE broadcasting from the notification consumer.
     */
    @EventListener
    public void onOrderStatusUpdated(OrderStatusUpdatedEvent event) {
        publishStatusUpdate(event.getOrderId(), event.getStatus());
    }
}
