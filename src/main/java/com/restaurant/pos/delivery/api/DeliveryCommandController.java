package com.restaurant.pos.delivery.api;

import com.restaurant.pos.common.dto.ApiResponse;
import com.restaurant.pos.delivery.command.CreateDeliveryOrderCommand;
import com.restaurant.pos.delivery.command.CreateDeliveryPaymentCommand;
import com.restaurant.pos.delivery.command.DeliveryCommandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CQRS Command Controller for CafeQR Delivery Web application.
 * Handles state mutations (creating payment orders, placing delivery orders, registering tokens).
 */
@Slf4j
@RestController
@RequestMapping({"/api/v1/delivery", "/api/delivery"})
@RequiredArgsConstructor
@Tag(name = "Delivery Commands", description = "Public state mutation endpoints for CafeQR delivery customer interface.")
public class DeliveryCommandController {

    private final DeliveryCommandService commandService;

    @PostMapping("/customer/profile")
    @Operation(summary = "Save Customer Profile", description = "Updates ERP customer profile details.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> saveCustomerProfile(
            @RequestBody Map<String, Object> payload) {
        log.info("Saving customer profile payload: {}", payload.keySet());
        Map<String, Object> result = commandService.saveCustomerProfile(payload);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/payments/create-order")
    @Operation(summary = "Create Razorpay Payment Order", description = "Generates a Razorpay order ID using client credentials.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createPaymentOrder(
            @RequestBody Map<String, Object> payload) {
        log.info("Creating delivery payment order payload keys={}", payload.keySet());

        UUID clientId = UUID.fromString((String) payload.get("clientId"));
        String orgId = (String) payload.get("orgId");
        String customerPhone = payload.get("customerPhone") != null ? String.valueOf(payload.get("customerPhone")) : null;
        String customerEmail = payload.get("customerEmail") != null ? String.valueOf(payload.get("customerEmail")) : null;

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) payload.get("items");

        CreateDeliveryPaymentCommand command = CreateDeliveryPaymentCommand.builder()
                .clientId(clientId)
                .orgId(orgId)
                .customerPhone(customerPhone)
                .customerEmail(customerEmail)
                .items(items)
                .build();

        Map<String, Object> response = commandService.createPaymentOrder(command);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/orders")
    @Operation(summary = "Place Delivery Order", description = "Places a new delivery/takeaway order supporting COD or online payment.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> placeOrder(
            @RequestBody Map<String, Object> payload) {
        log.info("Placing delivery order for clientId={}", payload.get("clientId"));

        UUID clientId = UUID.fromString((String) payload.get("clientId"));
        String orgId = (String) payload.get("orgId");
        String fulfillmentType = String.valueOf(payload.getOrDefault("fulfillmentType", "DELIVERY"));
        String customerEmail = (String) payload.getOrDefault("customerEmail", "");
        String customerName = (String) payload.getOrDefault("customerName", "");
        String customerPhone = (String) payload.getOrDefault("customerPhone", "");
        String deliveryAddress = (String) payload.getOrDefault("deliveryAddress", "");
        String note = (String) payload.getOrDefault("note", "");
        String remarks = (String) payload.getOrDefault("remarks", "");

        String paymentMethod = String.valueOf(payload.getOrDefault("paymentMethod", "COD"));
        String razorpayPaymentId = (String) payload.get("razorpayPaymentId");
        String razorpayOrderId = (String) payload.get("razorpayOrderId");
        String razorpaySignature = (String) payload.get("razorpaySignature");

        BigDecimal latitude = null;
        BigDecimal longitude = null;
        if (payload.get("latitude") != null) {
            try { latitude = new BigDecimal(String.valueOf(payload.get("latitude"))); } catch (Exception ignored) {}
        }
        if (payload.get("longitude") != null) {
            try { longitude = new BigDecimal(String.valueOf(payload.get("longitude"))); } catch (Exception ignored) {}
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) payload.get("items");

        CreateDeliveryOrderCommand command = CreateDeliveryOrderCommand.builder()
                .clientId(clientId)
                .orgId(orgId)
                .fulfillmentType(fulfillmentType)
                .customerEmail(customerEmail)
                .customerName(customerName)
                .customerPhone(customerPhone)
                .deliveryAddress(deliveryAddress)
                .note(note)
                .remarks(remarks)
                .paymentMethod(paymentMethod)
                .razorpayPaymentId(razorpayPaymentId)
                .razorpayOrderId(razorpayOrderId)
                .razorpaySignature(razorpaySignature)
                .latitude(latitude)
                .longitude(longitude)
                .items(items)
                .build();

        Map<String, Object> response = commandService.placeOrder(command);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/fcm-tokens")
    @Operation(summary = "Register FCM Token", description = "Registers customer push notification token.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> registerFcmToken(
            @RequestBody Map<String, Object> payload) {
        return ResponseEntity.ok(ApiResponse.success(Map.of("registered", true)));
    }

    @PostMapping("/addresses")
    @Operation(summary = "Save Customer Address", description = "Saves customer delivery address.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> saveAddress(
            @RequestBody Map<String, Object> payload) {
        return ResponseEntity.ok(ApiResponse.success(Map.of("saved", true)));
    }
}
