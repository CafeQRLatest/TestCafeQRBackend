package com.restaurant.pos.subscription.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.pos.auth.domain.User;
import com.restaurant.pos.common.dto.ApiResponse;
import com.restaurant.pos.common.exception.BusinessException;
import com.restaurant.pos.payment.service.RazorpayService;
import com.restaurant.pos.subscription.dto.SubscriptionActivationRequest;
import com.restaurant.pos.subscription.dto.SubscriptionPaymentResponse;
import com.restaurant.pos.subscription.dto.SubscriptionStatusResponse;
import com.restaurant.pos.subscription.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final RazorpayService razorpayService;
    private final ObjectMapper objectMapper;

    @GetMapping("/api/v1/subscription/status")
    public ResponseEntity<ApiResponse<SubscriptionStatusResponse>> status(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(subscriptionService.getStatus(currentClientId(authentication))));
    }

    @PostMapping("/api/v1/subscription/create-payment")
    public ResponseEntity<ApiResponse<SubscriptionPaymentResponse>> createPayment(
            Authentication authentication,
            @RequestBody com.restaurant.pos.subscription.dto.SubscriptionPaymentRequest request) {
        return ResponseEntity.ok(ApiResponse.success(subscriptionService.createPayment(currentClientId(authentication), request)));
    }

    @PostMapping("/api/v1/subscription/activate")
    public ResponseEntity<ApiResponse<SubscriptionStatusResponse>> activate(
            Authentication authentication,
            @RequestBody SubscriptionActivationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(subscriptionService.activate(currentClientId(authentication), request)));
    }

    @PostMapping("/api/v1/public/subscription/razorpay-callback-redirect/{clientId}")
    public void razorpayCallbackRedirect(
            @PathVariable UUID clientId,
            @RequestParam("razorpay_order_id") String orderId,
            @RequestParam("razorpay_payment_id") String paymentId,
            @RequestParam("razorpay_signature") String signature,
            @RequestParam("frontend_url") String frontendUrl,
            jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {

        SubscriptionActivationRequest request = new SubscriptionActivationRequest();
        request.setRazorpayOrderId(orderId);
        request.setRazorpayPaymentId(paymentId);
        request.setRazorpaySignature(signature);

        try {
            subscriptionService.activate(clientId, request);
            response.sendRedirect(frontendUrl + "?status=success&payment_id=" + paymentId);
        } catch (Exception e) {
            response.sendRedirect(frontendUrl + "?status=error&message=" + java.net.URLEncoder.encode(e.getMessage(), java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    @PostMapping("/api/v1/public/subscription/webhook")
    public ResponseEntity<String> webhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) throws Exception {
        if (!razorpayService.verifyWebhookSignature(rawBody, signature)) {
            return ResponseEntity.badRequest().body("Invalid signature");
        }

        JsonNode root = objectMapper.readTree(rawBody);
        if ("payment.captured".equals(root.path("event").asText())) {
            JsonNode notes = root.path("payload").path("payment").path("entity").path("notes");
            if ("subscription".equals(notes.path("purpose").asText()) && notes.hasNonNull("client_id")) {
                subscriptionService.activateFromWebhook(UUID.fromString(notes.path("client_id").asText()), notes);
            }
        }

        return ResponseEntity.ok("OK");
    }

    private UUID currentClientId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof User user)) {
            throw new BusinessException("Authentication required");
        }
        return user.getClientId();
    }
}
