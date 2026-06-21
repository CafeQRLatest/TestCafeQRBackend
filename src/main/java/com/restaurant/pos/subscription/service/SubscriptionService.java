package com.restaurant.pos.subscription.service;

import com.restaurant.pos.client.domain.Client;
import com.restaurant.pos.client.repository.ClientRepository;
import com.restaurant.pos.common.exception.BusinessException;
import com.restaurant.pos.common.exception.ResourceNotFoundException;
import com.restaurant.pos.payment.dto.RazorpayOrderResponse;
import com.restaurant.pos.payment.service.RazorpayService;
import com.restaurant.pos.subscription.dto.SubscriptionActivationRequest;
import com.restaurant.pos.subscription.dto.SubscriptionPaymentResponse;
import com.restaurant.pos.subscription.dto.SubscriptionStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private static final String ACTIVE = "ACTIVE";
    private static final String TRIAL = "TRIAL";
    private static final String EXPIRED = "EXPIRED";

    private final ClientRepository clientRepository;
    private final RazorpayService razorpayService;

    @Value("${subscription.monthly-amount-paise:${SUBSCRIPTION_MONTHLY_AMOUNT_PAISE:9900}}")
    private long monthlyAmountPaise;

    @Transactional(readOnly = true)
    public SubscriptionStatusResponse getStatus(UUID clientId) {
        Client client = findClient(clientId);
        return toStatus(client);
    }

    @Transactional
    public SubscriptionPaymentResponse createPayment(UUID clientId) {
        Client client = findClient(clientId);

        Map<String, Object> notes = new LinkedHashMap<>();
        notes.put("purpose", "subscription");
        notes.put("client_id", client.getId().toString());
        notes.put("client_name", client.getName() != null ? client.getName() : "");
        notes.put("client_email", client.getEmail() != null ? client.getEmail() : "");

        String receipt = "sub_" + client.getId().toString().substring(0, 8) + "_" + System.currentTimeMillis();
        RazorpayOrderResponse order = razorpayService.createOrder(monthlyAmountPaise, "INR", receipt, notes);

        return SubscriptionPaymentResponse.builder()
                .orderId(order.getOrderId())
                .amount(order.getAmount())
                .currency(order.getCurrency())
                .keyId(order.getKeyId())
                .planName("Business Pro")
                .description("Monthly Subscription - Rs " + (monthlyAmountPaise / 100))
                .build();
    }

    @Transactional
    public SubscriptionStatusResponse activate(UUID clientId, SubscriptionActivationRequest request) {
        boolean valid = razorpayService.verifyPaymentSignature(
                request.getRazorpayOrderId(),
                request.getRazorpayPaymentId(),
                request.getRazorpaySignature()
        );
        if (!valid) {
            throw new BusinessException("Razorpay payment signature verification failed");
        }

        Client client = findClient(clientId);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime baseDate = client.getSubscriptionExpiryDate() != null
                && client.getSubscriptionExpiryDate().isAfter(now)
                ? client.getSubscriptionExpiryDate()
                : now;

        client.setSubscriptionStatus(ACTIVE);
        client.setSubscriptionExpiryDate(baseDate.plusMonths(1));
        clientRepository.save(client);

        return toStatus(client);
    }

    @Transactional
    public SubscriptionStatusResponse activateFromWebhook(UUID clientId) {
        Client client = findClient(clientId);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime baseDate = client.getSubscriptionExpiryDate() != null
                && client.getSubscriptionExpiryDate().isAfter(now)
                ? client.getSubscriptionExpiryDate()
                : now;
        client.setSubscriptionStatus(ACTIVE);
        client.setSubscriptionExpiryDate(baseDate.plusMonths(1));
        clientRepository.save(client);
        return toStatus(client);
    }

    private Client findClient(UUID clientId) {
        if (clientId == null) {
            throw new BusinessException("Client context is missing");
        }
        return clientRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found with id: " + clientId));
    }

    private SubscriptionStatusResponse toStatus(Client client) {
        LocalDateTime now = LocalDateTime.now();
        String status = normalizeStatus(client.getSubscriptionStatus());
        LocalDateTime expiry = client.getSubscriptionExpiryDate();
        boolean active = (ACTIVE.equals(status) || TRIAL.equals(status)) && expiry != null && !expiry.isBefore(now);
        int daysLeft = active ? Math.max(0, (int) Math.ceil(Duration.between(now, expiry).toHours() / 24.0)) : 0;

        if (!active && (ACTIVE.equals(status) || TRIAL.equals(status))) {
            status = EXPIRED;
        }

        String message = active
                ? (TRIAL.equals(status) ? "Free trial active" : "Paid subscription active")
                : "Subscription expired";

        return SubscriptionStatusResponse.builder()
                .active(active)
                .status(status)
                .daysLeft(daysLeft)
                .expiryDate(expiry)
                .message(message)
                .build();
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return EXPIRED;
        }
        return status.trim().toUpperCase();
    }
}
