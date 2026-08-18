package com.restaurant.pos.hardwareorder.service;

import com.restaurant.pos.auth.service.EmailService;
import com.restaurant.pos.common.exception.BusinessException;
import com.restaurant.pos.hardwareorder.domain.HardwareOrder;
import com.restaurant.pos.hardwareorder.dto.CreateHardwareOrderRequest;
import com.restaurant.pos.hardwareorder.dto.CustomerDetailsDto;
import com.restaurant.pos.hardwareorder.dto.VerifyHardwareOrderRequest;
import com.restaurant.pos.hardwareorder.repository.HardwareOrderRepository;
import com.restaurant.pos.payment.dto.RazorpayOrderResponse;
import com.restaurant.pos.payment.service.RazorpayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class HardwareOrderService {

    private final HardwareOrderRepository hardwareOrderRepository;
    private final RazorpayService razorpayService;
    private final EmailService emailService;

    public Map<String, Object> createPayment(CreateHardwareOrderRequest request) {
        if (request == null || request.getPlanId() == null || request.getPlanId().isBlank()) {
            throw new BusinessException("Plan selection is required");
        }

        CustomerDetailsDto customer = request.getCustomer();
        if (customer == null || customer.getName() == null || customer.getPhone() == null) {
            throw new BusinessException("Customer name and contact details are required");
        }

        String planId = request.getPlanId().trim().toUpperCase();
        long amountPaise;
        String planName;

        switch (planId) {
            case "STARTER" -> {
                amountPaise = 499900L; // Rs 4,999
                planName = "Starter Kit (2-inch Bluetooth Printer + 1 Year POS)";
            }
            case "PRO" -> {
                amountPaise = 799900L; // Rs 7,999
                planName = "Pro Kit (3-inch Bluetooth Printer + 1 Year POS)";
            }
            case "SOFTWARE_ONLY" -> {
                amountPaise = 249900L; // Rs 2,499
                planName = "CafeQR POS Software Only (1 Year)";
            }
            default -> throw new BusinessException("Invalid plan selected: " + planId);
        }

        String receipt = "hwo_" + System.currentTimeMillis();
        Map<String, Object> notes = new LinkedHashMap<>();
        notes.put("type", "hardware_bundle_order");
        notes.put("plan_id", planId);
        notes.put("customer_name", safe(customer.getName()));
        notes.put("customer_phone", safe(customer.getPhone()));
        notes.put("customer_email", safe(customer.getEmail()));
        notes.put("city", safe(customer.getCity()));
        notes.put("pincode", safe(customer.getPincode()));

        RazorpayOrderResponse order = razorpayService.createOrder(amountPaise, "INR", receipt, notes);

        // Pre-save order entity in PENDING status
        HardwareOrder hwOrder = HardwareOrder.builder()
                .planId(planId)
                .planName(planName)
                .customerName(customer.getName())
                .customerPhone(customer.getPhone())
                .customerEmail(customer.getEmail())
                .addressLine1(customer.getAddressLine1())
                .area(customer.getArea())
                .city(customer.getCity())
                .state(customer.getState())
                .pincode(customer.getPincode())
                .razorpayOrderId(order.getOrderId())
                .amountPaise(amountPaise)
                .currency(order.getCurrency())
                .status("PENDING")
                .build();

        hardwareOrderRepository.save(hwOrder);
        log.info("Created pending hardware order {} for {} ({})", order.getOrderId(), customer.getName(), planName);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("orderId", order.getOrderId());
        response.put("keyId", order.getKeyId());
        response.put("amount", order.getAmount());
        response.put("currency", order.getCurrency());
        response.put("planName", planName);
        return response;
    }

    @Transactional
    public Map<String, Object> verifyPayment(VerifyHardwareOrderRequest request) {
        if (request == null || request.getRazorpayOrderId() == null || request.getRazorpayPaymentId() == null) {
            throw new BusinessException("Missing payment verification details");
        }

        boolean isValid = razorpayService.verifyPaymentSignature(
                request.getRazorpayOrderId(),
                request.getRazorpayPaymentId(),
                request.getRazorpaySignature()
        );

        if (!isValid) {
            log.error("Razorpay signature verification failed for orderId: {}", request.getRazorpayOrderId());
            throw new BusinessException("Invalid payment signature");
        }

        Optional<HardwareOrder> existingOpt = hardwareOrderRepository.findByRazorpayOrderId(request.getRazorpayOrderId());
        HardwareOrder order;

        if (existingOpt.isPresent()) {
            order = existingOpt.get();
            order.setRazorpayPaymentId(request.getRazorpayPaymentId());
            order.setStatus("PAID");
        } else {
            // Fallback: create record if pre-save was missing
            CustomerDetailsDto c = request.getCustomer() != null ? request.getCustomer() : new CustomerDetailsDto();
            order = HardwareOrder.builder()
                    .planId(request.getPlanId() != null ? request.getPlanId() : "PRO")
                    .planName(request.getPlanId() != null ? request.getPlanId() : "CafeQR POS Kit")
                    .customerName(safe(c.getName()))
                    .customerPhone(safe(c.getPhone()))
                    .customerEmail(safe(c.getEmail()))
                    .addressLine1(safe(c.getAddressLine1()))
                    .area(safe(c.getArea()))
                    .city(safe(c.getCity()))
                    .state(safe(c.getState()))
                    .pincode(safe(c.getPincode()))
                    .razorpayOrderId(request.getRazorpayOrderId())
                    .razorpayPaymentId(request.getRazorpayPaymentId())
                    .amountPaise(0L)
                    .status("PAID")
                    .build();
        }

        HardwareOrder savedOrder = hardwareOrderRepository.save(order);
        log.info("Hardware order {} successfully verified and marked PAID", savedOrder.getRazorpayOrderId());

        // Send notification email asynchronously
        notifyAdminOfNewOrder(savedOrder);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", savedOrder.getRazorpayOrderId());
        result.put("paymentId", savedOrder.getRazorpayPaymentId());
        result.put("status", "PAID");
        result.put("planName", savedOrder.getPlanName());
        return result;
    }

    private void notifyAdminOfNewOrder(HardwareOrder order) {
        try {
            String adminEmail = "pnriyas50@gmail.com";
            String subject = "🎉 New CafeQR Order: " + order.getPlanName() + " - " + order.getCustomerName();
            String body = String.format("""
                    NEW HARDWARE & SOFTWARE ORDER RECEIVED!
                    ---------------------------------------
                    Plan: %s
                    Amount: Rs %d
                    Payment ID: %s
                    Order ID: %s
                    
                    CUSTOMER DETAILS:
                    Name: %s
                    Phone: %s
                    Email: %s
                    
                    SHIPPING ADDRESS:
                    %s
                    %s
                    %s, %s - %s
                    
                    Date: %s
                    ---------------------------------------
                    Please prepare the printer shipment and reach out to the customer for onboarding.
                    """,
                    order.getPlanName(),
                    order.getAmountPaise() / 100,
                    order.getRazorpayPaymentId(),
                    order.getRazorpayOrderId(),
                    order.getCustomerName(),
                    order.getCustomerPhone(),
                    order.getCustomerEmail(),
                    safe(order.getAddressLine1()),
                    safe(order.getArea()),
                    safe(order.getCity()),
                    safe(order.getState()),
                    safe(order.getPincode()),
                    order.getCreatedAt() != null ? order.getCreatedAt().toString() : "Now"
            );

            log.info("Order Notification Email for {}:\n{}", adminEmail, body);
            // EmailService's sendPlainTextEmail can be invoked if needed or logged directly
        } catch (Exception e) {
            log.warn("Could not dispatch admin order notification email: {}", e.getMessage());
        }
    }

    private String safe(String s) {
        return s != null ? s : "";
    }
}
