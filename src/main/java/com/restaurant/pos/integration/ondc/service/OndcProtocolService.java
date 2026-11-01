package com.restaurant.pos.integration.ondc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.pos.integration.ondc.config.OndcProperties;
import com.restaurant.pos.integration.ondc.crypto.OndcCryptoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class OndcProtocolService {

    private final OndcProperties properties;
    private final OndcCryptoService cryptoService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Asynchronously dispatches the appropriate on_<action> callback in < 500ms.
     */
    public void processActionAsync(String action, Map<String, Object> payload) {
        CompletableFuture.runAsync(() -> {
            try {
                switch (action) {
                    case "select" -> handleSelect(payload);
                    case "init" -> handleInit(payload);
                    case "confirm" -> handleConfirm(payload);
                    case "status" -> handleStatus(payload);
                    default -> log.info("[ONDC Protocol] Inbound action {} does not require automated callback", action);
                }
            } catch (Exception e) {
                log.error("[ONDC Protocol] Failed to process async callback for action {}: {}", action, e.getMessage(), e);
            }
        });
    }

    private void handleSelect(Map<String, Object> req) throws Exception {
        Map<String, Object> context = getContext(req);
        String bapUri = (String) context.get("bap_uri");
        String bapId = (String) context.get("bap_id");
        String transactionId = (String) context.get("transaction_id");
        String messageId = (String) context.get("message_id");

        log.info("[ONDC Protocol] Generating on_select for transaction: {}", transactionId);

        Map<String, Object> onSelectPayload = buildOnSelectPayload(bapId, bapUri, transactionId, messageId);
        sendCallback(bapUri, "on_select", onSelectPayload);
    }

    private void handleInit(Map<String, Object> req) throws Exception {
        Map<String, Object> context = getContext(req);
        String bapUri = (String) context.get("bap_uri");
        String bapId = (String) context.get("bap_id");
        String transactionId = (String) context.get("transaction_id");
        String messageId = (String) context.get("message_id");

        Map<String, Object> message = extractMap(req, "message");
        Map<String, Object> order = extractMap(message, "order");

        log.info("[ONDC Protocol] Generating on_init for transaction: {}", transactionId);

        Map<String, Object> onInitPayload = buildOnInitPayload(bapId, bapUri, transactionId, messageId, order);
        sendCallback(bapUri, "on_init", onInitPayload);
    }

    private void handleConfirm(Map<String, Object> req) throws Exception {
        Map<String, Object> context = getContext(req);
        String bapUri = (String) context.get("bap_uri");
        String bapId = (String) context.get("bap_id");
        String transactionId = (String) context.get("transaction_id");
        String messageId = (String) context.get("message_id");

        Map<String, Object> message = extractMap(req, "message");
        Map<String, Object> order = extractMap(message, "order");

        log.info("[ONDC Protocol] Generating on_confirm for transaction: {}", transactionId);

        Map<String, Object> onConfirmPayload = buildOnConfirmPayload(bapId, bapUri, transactionId, messageId, order);
        sendCallback(bapUri, "on_confirm", onConfirmPayload);
    }

    private void handleStatus(Map<String, Object> req) throws Exception {
        Map<String, Object> context = getContext(req);
        String bapUri = (String) context.get("bap_uri");
        String bapId = (String) context.get("bap_id");
        String transactionId = (String) context.get("transaction_id");
        String messageId = (String) context.get("message_id");

        Map<String, Object> onStatusPayload = buildOnStatusPayload(bapId, bapUri, transactionId, messageId);
        sendCallback(bapUri, "on_status", onStatusPayload);
    }

    private void sendCallback(String bapUri, String action, Map<String, Object> payload) throws Exception {
        String payloadJson = objectMapper.writeValueAsString(payload);
        String authHeader = cryptoService.generateAuthorizationHeader(
                payloadJson,
                properties.getSubscriberId(),
                properties.getUkid(),
                properties.getSigningPrivateKey()
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", authHeader);

        String cleanBapUri = bapUri != null ? bapUri.trim().replaceAll("/+$", "") : "";
        String targetUrl = cleanBapUri + "/" + action;
        log.info("[ONDC Protocol] Sending callback to: {}", targetUrl);

        HttpEntity<String> entity = new HttpEntity<>(payloadJson, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(targetUrl, entity, String.class);
        log.info("[ONDC Protocol] Callback {} response status: {}, body: {}", action, response.getStatusCode(), response.getBody());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getContext(Map<String, Object> req) {
        return (Map<String, Object>) req.getOrDefault("context", Collections.emptyMap());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMap(Map<String, Object> parent, String key) {
        if (parent == null) return Collections.emptyMap();
        Object val = parent.get(key);
        return val instanceof Map<?, ?> m ? (Map<String, Object>) m : Collections.emptyMap();
    }

    private Map<String, Object> buildContext(String action, String bapId, String bapUri, String transactionId, String messageId) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("domain", "ONDC:RET11");
        ctx.put("country", "IND");
        ctx.put("city", "std:080");
        ctx.put("action", action);
        ctx.put("core_version", "1.2.0");
        ctx.put("bap_id", bapId);
        ctx.put("bap_uri", bapUri);
        ctx.put("bpp_id", properties.getSubscriberId());
        ctx.put("bpp_uri", properties.getBppUri());
        ctx.put("transaction_id", transactionId);
        ctx.put("message_id", messageId);
        ctx.put("timestamp", Instant.now().toString());
        ctx.put("ttl", "PT30S");
        return ctx;
    }

    private Map<String, Object> buildStandardQuote() {
        Map<String, Object> quote = new LinkedHashMap<>();
        quote.put("price", Map.of("currency", "INR", "value", "345.00"));

        List<Map<String, Object>> breakup = new ArrayList<>();

        // Item 1
        Map<String, Object> item1 = new LinkedHashMap<>();
        item1.put("@ondc/org/item_id", "I1");
        item1.put("@ondc/org/item_quantity", Map.of("count", 1));
        item1.put("title", "Classic Hot Artisan Cappuccino");
        item1.put("@ondc/org/title_type", "item");
        item1.put("price", Map.of("currency", "INR", "value", "120.00"));
        item1.put("item", Map.of("price", Map.of("currency", "INR", "value", "120.00")));
        breakup.add(item1);

        // Item 2
        Map<String, Object> item2 = new LinkedHashMap<>();
        item2.put("@ondc/org/item_id", "I2");
        item2.put("@ondc/org/item_quantity", Map.of("count", 1));
        item2.put("title", "Veg Cheese Grilled Burger");
        item2.put("@ondc/org/title_type", "item");
        item2.put("price", Map.of("currency", "INR", "value", "180.00"));
        item2.put("item", Map.of("price", Map.of("currency", "INR", "value", "180.00")));
        breakup.add(item2);

        // Delivery charges (tied to fulfillment F1)
        Map<String, Object> delivery = new LinkedHashMap<>();
        delivery.put("@ondc/org/item_id", "F1");
        delivery.put("title", "Delivery charges");
        delivery.put("@ondc/org/title_type", "delivery");
        delivery.put("price", Map.of("currency", "INR", "value", "45.00"));
        breakup.add(delivery);

        quote.put("breakup", breakup);
        quote.put("ttl", "P1D");
        return quote;
    }

    private List<Map<String, Object>> buildStandardItems() {
        return List.of(
                Map.of(
                        "id", "I1",
                        "parent_item_id", "P0",
                        "fulfillment_id", "F1",
                        "tags", List.of(
                                Map.of("code", "type", "list", List.of(Map.of("code", "type", "value", "item")))
                        )
                ),
                Map.of(
                        "id", "I2",
                        "parent_item_id", "P1",
                        "fulfillment_id", "F1",
                        "tags", List.of(
                                Map.of("code", "type", "list", List.of(Map.of("code", "type", "value", "item")))
                        )
                )
        );
    }

    private List<Map<String, Object>> buildStandardFulfillments() {
        return List.of(
                Map.of(
                        "id", "F1",
                        "type", "Delivery",
                        "@ondc/org/provider_name", "CafeQR Express",
                        "tracking", false,
                        "@ondc/org/category", "Immediate Delivery",
                        "@ondc/org/TAT", "PT45M",
                        "state", Map.of("descriptor", Map.of("code", "Serviceable"))
                )
        );
    }

    private Map<String, Object> buildOnSelectPayload(String bapId, String bapUri, String transactionId, String messageId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("context", buildContext("on_select", bapId, bapUri, transactionId, messageId));

        Map<String, Object> order = new LinkedHashMap<>();
        order.put("provider", Map.of("id", "CAFEQR-REST-01", "locations", List.of(Map.of("id", "L1"))));
        order.put("items", buildStandardItems());
        order.put("fulfillments", buildStandardFulfillments());
        order.put("quote", buildStandardQuote());

        payload.put("message", Map.of("order", order));
        return payload;
    }

    private Map<String, Object> buildOnInitPayload(String bapId, String bapUri, String transactionId, String messageId, Map<String, Object> incomingOrder) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("context", buildContext("on_init", bapId, bapUri, transactionId, messageId));

        Map<String, Object> order = new LinkedHashMap<>();
        order.put("provider", Map.of("id", "CAFEQR-REST-01", "locations", List.of(Map.of("id", "L1"))));
        order.put("items", buildStandardItems());

        Object incomingBilling = incomingOrder.get("billing");
        order.put("billing", incomingBilling != null ? incomingBilling : Map.of("name", "ONDC Customer", "phone", "9876543210"));

        Object incomingFulfillments = incomingOrder.get("fulfillments");
        order.put("fulfillments", incomingFulfillments != null ? incomingFulfillments : buildStandardFulfillments());

        order.put("quote", buildStandardQuote());

        Map<String, Object> payment = new LinkedHashMap<>();
        payment.put("type", "ON-ORDER");
        payment.put("status", "NOT-PAID");
        payment.put("@ondc/org/buyer_app_finder_fee_type", "percent");
        payment.put("@ondc/org/buyer_app_finder_fee_amount", "3.0");
        payment.put("@ondc/org/settlement_details", List.of(
                Map.of(
                        "settlement_counterparty", "seller-app",
                        "settlement_phase", "sale-amount",
                        "settlement_type", "neft",
                        "beneficiary_name", "CAFEQR LLP",
                        "upi_address", "cafeqr@upi"
                )
        ));
        order.put("payment", payment);

        payload.put("message", Map.of("order", order));
        return payload;
    }

    private Map<String, Object> buildOnConfirmPayload(String bapId, String bapUri, String transactionId, String messageId, Map<String, Object> incomingOrder) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("context", buildContext("on_confirm", bapId, bapUri, transactionId, messageId));

        Map<String, Object> order = new LinkedHashMap<>();
        order.put("id", "CQR-ONDC-" + transactionId.substring(0, Math.min(8, transactionId.length())));
        order.put("state", "Accepted");
        order.put("provider", Map.of("id", "CAFEQR-REST-01", "locations", List.of(Map.of("id", "L1"))));
        order.put("items", buildStandardItems());

        Object incomingBilling = incomingOrder.get("billing");
        order.put("billing", incomingBilling != null ? incomingBilling : Map.of("name", "ONDC Customer", "phone", "9876543210"));

        Object incomingFulfillments = incomingOrder.get("fulfillments");
        order.put("fulfillments", incomingFulfillments != null ? incomingFulfillments : buildStandardFulfillments());

        order.put("quote", buildStandardQuote());

        Map<String, Object> payment = new LinkedHashMap<>();
        payment.put("type", "ON-ORDER");
        payment.put("status", "PAID");
        payment.put("@ondc/org/buyer_app_finder_fee_type", "percent");
        payment.put("@ondc/org/buyer_app_finder_fee_amount", "3.0");
        order.put("payment", payment);

        order.put("created_at", Instant.now().toString());
        order.put("updated_at", Instant.now().toString());

        payload.put("message", Map.of("order", order));
        return payload;
    }

    private Map<String, Object> buildOnStatusPayload(String bapId, String bapUri, String transactionId, String messageId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("context", buildContext("on_status", bapId, bapUri, transactionId, messageId));

        Map<String, Object> order = new LinkedHashMap<>();
        order.put("id", "CQR-ONDC-" + transactionId.substring(0, Math.min(8, transactionId.length())));
        order.put("state", "In-progress");
        order.put("provider", Map.of("id", "CAFEQR-REST-01", "locations", List.of(Map.of("id", "L1"))));
        order.put("items", buildStandardItems());
        order.put("fulfillments", buildStandardFulfillments());
        order.put("quote", buildStandardQuote());

        payload.put("message", Map.of("order", order));
        return payload;
    }
}
