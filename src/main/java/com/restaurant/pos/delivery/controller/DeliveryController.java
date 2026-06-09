package com.restaurant.pos.delivery.controller;

import com.restaurant.pos.client.repository.ClientRepository;
import com.restaurant.pos.client.repository.OrganizationRepository;
import com.restaurant.pos.common.dto.ApiResponse;
import com.restaurant.pos.common.exception.BusinessException;
import com.restaurant.pos.common.exception.ResourceNotFoundException;
import com.restaurant.pos.order.domain.Order;
import com.restaurant.pos.order.domain.OrderLine;
import com.restaurant.pos.order.domain.OrderType;
import com.restaurant.pos.order.repository.OrderRepository;
import com.restaurant.pos.product.domain.Product;
import com.restaurant.pos.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Public (unauthenticated) API for the CafeQR Delivery Website.
 *
 * All endpoints are scoped to a clientId so a single backend instance
 * can serve multiple restaurants. No staff/POS auth is required —
 * customers interact directly with these endpoints.
 *
 * Base path: /delivery
 *
 * Endpoints:
 *   GET  /delivery/restaurant/{clientId}/settings        — brand + restaurant info
 *   GET  /delivery/restaurant/{clientId}/menu            — active menu items
 *   POST /delivery/orders                                — place a delivery/takeaway order
 *   GET  /delivery/orders/{orderId}                      — track a single order
 *   GET  /delivery/orders?clientId=&email=               — list orders for a customer email
 *   POST /delivery/fcm-tokens                            — register FCM push token (stub)
 *   GET  /delivery/addresses?clientId=&email=            — list saved addresses (stub)
 *   POST /delivery/addresses                             — save a delivery address (stub)
 */
@RestController
@RequestMapping("/delivery")
@RequiredArgsConstructor
public class DeliveryController {

    private final ClientRepository       clientRepository;
    private final OrganizationRepository organizationRepository;
    private final ProductRepository      productRepository;
    private final OrderRepository        orderRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // 1. GET /delivery/restaurant/{clientId}/settings
    // Returns brand colour, name, logo, contact info, delivery toggle.
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/restaurant/{clientId}/settings")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSettings(
            @PathVariable UUID clientId) {

        var client = clientRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));

        if (!client.isSubscriptionActive()) {
            throw new BusinessException("Restaurant subscription is inactive.");
        }

        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("clientId",         clientId);
        settings.put("restaurantName",   nvl(client.getName(), "Our Restaurant"));
        settings.put("logoUrl",          client.getLogoUrl());
        settings.put("brandColor",       nvl(client.getBrandColor(), "#f97316"));
        settings.put("address",          client.getAddress());
        settings.put("phone",            client.getPhone());
        settings.put("whatsappNumber",   client.getWhatsappNumber());
        settings.put("currency",         nvl(client.getCurrency(), "INR"));
        settings.put("timezone",         nvl(client.getTimezone(), "Asia/Kolkata"));
        settings.put("googleMapsUrl",    client.getGoogleMapsUrl());
        settings.put("instagramUrl",     client.getInstagramUrl());
        settings.put("facebookUrl",      client.getFacebookUrl());
        settings.put("deliveryEnabled",  true);   // Always true on this endpoint
        settings.put("takeawayEnabled",  true);
        settings.put("minOrderAmount",   BigDecimal.ZERO);
        settings.put("estimatedDeliveryMinutes", 45);

        return ResponseEntity.ok(ApiResponse.success(settings));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. GET /delivery/restaurant/{clientId}/menu
    // Returns active, available products scoped to clientId.
    // Optional ?orgId= to narrow to a specific branch.
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/restaurant/{clientId}/menu")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getMenu(
            @PathVariable UUID clientId,
            @RequestParam(required = false) String orgId) {

        validateSubscription(clientId);

        UUID orgUuid = parseOrgId(orgId);
        List<Product> products = productRepository
                .findByClientIdAndOrgIdOrGlobalAndIsActiveTrue(clientId, orgUuid);

        List<Map<String, Object>> menu = products.stream()
                .filter(Product::isAvailable)
                .map(p -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id",          p.getId());
                    item.put("name",        p.getName());
                    item.put("description", p.getDescription());
                    item.put("price",       p.getPrice());
                    item.put("imageUrl",    p.getImageUrl());
                    item.put("category",    p.getCategory() != null ? p.getCategory().getName() : "Others");
                    item.put("isVeg",       !p.isPackagedGood());
                    item.put("isAvailable", p.isAvailable());
                    item.put("productType", p.getProductType());
                    return item;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(menu));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. POST /delivery/orders
    // Places a new delivery or takeaway order.
    //
    // Expected request body:
    // {
    //   "clientId":        "uuid",
    //   "orgId":           "uuid" | null,
    //   "customerEmail":   "string",
    //   "customerName":    "string",
    //   "customerPhone":   "string",
    //   "fulfillmentType": "DELIVERY" | "TAKEAWAY",
    //   "deliveryAddress": "string",
    //   "note":            "string",
    //   "items": [
    //     { "productId": "uuid", "quantity": 2 }
    //   ]
    // }
    // ─────────────────────────────────────────────────────────────────────────
    @PostMapping("/orders")
    public ResponseEntity<ApiResponse<Map<String, Object>>> placeOrder(
            @RequestBody Map<String, Object> payload) {

        UUID clientId = UUID.fromString((String) payload.get("clientId"));
        validateSubscription(clientId);

        UUID orgUuid          = parseOrgId((String) payload.get("orgId"));
        String fulfillment    = String.valueOf(payload.getOrDefault("fulfillmentType", "DELIVERY")).toUpperCase();
        String customerEmail  = (String) payload.getOrDefault("customerEmail", "");
        String customerName   = (String) payload.getOrDefault("customerName",  "");
        String customerPhone  = (String) payload.getOrDefault("customerPhone", "");
        String deliveryAddress= (String) payload.getOrDefault("deliveryAddress", "");
        String note           = (String) payload.getOrDefault("note", "");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) payload.get("items");

        if (items == null || items.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.success(Map.of("error", "No items in order")));
        }

        String orderNo = "DEL-" + System.currentTimeMillis();
        String description = buildDescription(customerEmail, customerName, customerPhone, deliveryAddress, note);

        Order order = Order.builder()
                .id(UUID.randomUUID())
                .orderNo(orderNo)
                .orderType(OrderType.SALE)
                .orderStatus("CONFIRMED")
                .paymentStatus("PENDING")
                .orderSource("DELIVERY_WEB")
                .fulfillmentType(fulfillment)
                .description(description)
                .orderDate(Instant.now())
                .clientId(clientId)
                .orgId(orgUuid)
                .build();

        BigDecimal grandTotal = BigDecimal.ZERO;

        for (Map<String, Object> cartItem : items) {
            UUID productId = UUID.fromString((String) cartItem.get("productId"));
            int qty = ((Number) cartItem.get("quantity")).intValue();

            Optional<Product> productOpt = productRepository.findById(productId)
                    .filter(p -> clientId.equals(p.getClientId()))
                    .filter(p -> orgUuid == null || p.getOrgId() == null || orgUuid.equals(p.getOrgId()))
                    .filter(Product::isActive)
                    .filter(Product::isAvailable);

            if (productOpt.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.success(Map.of("error", "Invalid or unavailable item: " + productId)));
            }

            Product p = productOpt.get();
            BigDecimal lineTotal = p.getPrice().multiply(BigDecimal.valueOf(qty));

            OrderLine line = OrderLine.builder()
                    .productId(productId)
                    .productName(p.getName())
                    .categoryName(p.getCategory() != null ? p.getCategory().getName() : null)
                    .isPackagedGood(p.isPackagedGood())
                    .quantity(BigDecimal.valueOf(qty))
                    .unitPrice(p.getPrice())
                    .lineTotal(lineTotal)
                    .build();

            order.addLine(line);
            grandTotal = grandTotal.add(lineTotal);
        }

        order.setTotalAmount(grandTotal);
        order.setGrandTotal(grandTotal);

        Order saved = orderRepository.save(order);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("orderId",         saved.getId());
        response.put("orderNo",         saved.getOrderNo());
        response.put("status",          saved.getOrderStatus());
        response.put("paymentStatus",   saved.getPaymentStatus());
        response.put("fulfillmentType", saved.getFulfillmentType());
        response.put("grandTotal",      saved.getGrandTotal());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. GET /delivery/orders/{orderId}
    // Returns full order details for order tracking.
    // ?clientId= required for tenant scoping.
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/orders/{orderId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOrder(
            @PathVariable UUID orderId,
            @RequestParam UUID clientId) {

        Order order = orderRepository.findByIdAndClientId(orderId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        return ResponseEntity.ok(ApiResponse.success(toOrderMap(order)));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. GET /delivery/orders
    // Lists orders for a customer by email + clientId.
    // ?clientId= &email= (both required)
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listOrders(
            @RequestParam UUID clientId,
            @RequestParam String email) {

        // Filter DELIVERY_WEB orders whose description contains the email
        List<Order> orders = orderRepository.findByClientIdAndOrderStatusIn(
                        clientId,
                        List.of("CONFIRMED", "PREPARING", "OUT_FOR_DELIVERY", "DELIVERED", "COMPLETED", "CANCELLED"))
                .stream()
                .filter(o -> "DELIVERY_WEB".equals(o.getOrderSource()))
                .filter(o -> o.getDescription() != null && o.getDescription().contains(email))
                .sorted(Comparator.comparing(Order::getOrderDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        List<Map<String, Object>> result = orders.stream()
                .map(this::toOrderMap)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. POST /delivery/fcm-tokens
    // Registers a customer FCM push token for order status notifications.
    // Stored as a no-op stub — full FCM fan-out requires a notifications table.
    // ─────────────────────────────────────────────────────────────────────────
    @PostMapping("/fcm-tokens")
    public ResponseEntity<ApiResponse<Map<String, Object>>> registerFcmToken(
            @RequestBody Map<String, Object> payload) {
        // Stub — accept and acknowledge the token, fire-and-forget
        // Full implementation: persist to a customer_fcm_tokens table and
        // fan-out via Firebase Admin SDK when order status changes.
        return ResponseEntity.ok(ApiResponse.success(Map.of("registered", true)));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. GET /delivery/addresses
    // Returns saved delivery addresses for a customer.
    // Stub — returns empty list until a customer_addresses table is added.
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/addresses")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAddresses(
            @RequestParam UUID clientId,
            @RequestParam String email) {
        // Stub — full implementation requires a customer_addresses table
        return ResponseEntity.ok(ApiResponse.success(Collections.emptyList()));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. POST /delivery/addresses
    // Saves a new delivery address for a customer.
    // Stub — accepts and acknowledges until persistence layer is ready.
    // ─────────────────────────────────────────────────────────────────────────
    @PostMapping("/addresses")
    public ResponseEntity<ApiResponse<Map<String, Object>>> saveAddress(
            @RequestBody Map<String, Object> payload) {
        // Stub — full implementation requires a customer_addresses table
        return ResponseEntity.ok(ApiResponse.success(Map.of("saved", true)));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void validateSubscription(UUID clientId) {
        var client = clientRepository.findById(clientId).orElse(null);
        if (client == null || !client.isSubscriptionActive()) {
            throw new BusinessException("Restaurant subscription is inactive or not found.");
        }
    }

    private UUID parseOrgId(String orgId) {
        if (orgId == null || orgId.isBlank() || "null".equalsIgnoreCase(orgId)) return null;
        try { return UUID.fromString(orgId); } catch (IllegalArgumentException e) { return null; }
    }

    private String nvl(String value, String fallback) {
        return (value != null && !value.isBlank()) ? value : fallback;
    }

    private String buildDescription(String email, String name, String phone, String address, String note) {
        StringBuilder sb = new StringBuilder();
        if (email   != null && !email.isBlank())   sb.append("email:").append(email).append(" ");
        if (name    != null && !name.isBlank())    sb.append("name:").append(name).append(" ");
        if (phone   != null && !phone.isBlank())   sb.append("phone:").append(phone).append(" ");
        if (address != null && !address.isBlank()) sb.append("address:").append(address).append(" ");
        if (note    != null && !note.isBlank())    sb.append("note:").append(note);
        return sb.toString().trim();
    }

    private Map<String, Object> toOrderMap(Order order) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("orderId",         order.getId());
        map.put("orderNo",         order.getOrderNo());
        map.put("status",          order.getOrderStatus());
        map.put("paymentStatus",   order.getPaymentStatus());
        map.put("fulfillmentType", order.getFulfillmentType());
        map.put("grandTotal",      order.getGrandTotal());
        map.put("orderDate",       order.getOrderDate());
        map.put("description",     order.getDescription());

        if (order.getLines() != null) {
            List<Map<String, Object>> lines = order.getLines().stream().map(l -> {
                Map<String, Object> lm = new LinkedHashMap<>();
                lm.put("productId",   l.getProductId());
                lm.put("productName", l.getProductName());
                lm.put("quantity",    l.getQuantity());
                lm.put("unitPrice",   l.getUnitPrice());
                lm.put("lineTotal",   l.getLineTotal());
                return lm;
            }).collect(Collectors.toList());
            map.put("items", lines);
        }

        return map;
    }
}
