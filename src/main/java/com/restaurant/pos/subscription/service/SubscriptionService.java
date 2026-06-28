package com.restaurant.pos.subscription.service;

import com.restaurant.pos.client.domain.Client;
import com.restaurant.pos.client.repository.ClientRepository;
import com.restaurant.pos.common.context.TimezoneResolver;
import com.restaurant.pos.common.exception.BusinessException;
import com.restaurant.pos.common.exception.ResourceNotFoundException;
import com.restaurant.pos.payment.dto.RazorpayOrderResponse;
import com.restaurant.pos.payment.service.RazorpayService;
import com.restaurant.pos.subscription.domain.ClientSubscriptionModule;
import com.restaurant.pos.subscription.domain.ModuleName;
import com.restaurant.pos.subscription.dto.SubscriptionActivationRequest;
import com.restaurant.pos.subscription.dto.SubscriptionPaymentRequest;
import com.restaurant.pos.subscription.dto.SubscriptionPaymentResponse;
import com.restaurant.pos.subscription.dto.SubscriptionStatusResponse;
import com.restaurant.pos.subscription.repository.ClientSubscriptionModuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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
    private final TimezoneResolver timezoneResolver;
    private final ClientSubscriptionModuleRepository clientSubscriptionModuleRepository;

    // Monthly Prices in Paise (1 INR = 100 paise)
    private static final long PRICE_BASE_MONTHLY = 9900;          // ₹99
    private static final long PRICE_KOT_MONTHLY = 5000;           // ₹50
    private static final long PRICE_INVENTORY_MONTHLY = 19900;    // ₹199
    private static final long PRICE_CRM_MONTHLY = 9900;           // ₹99
    private static final long PRICE_CREDIT_LEDGER_MONTHLY = 4900;  // ₹49
    private static final long PRICE_TABLE_QR_MONTHLY = 0;         // Free
    private static final long PRICE_MENU_IMAGES_MONTHLY = 0;       // Free
    private static final long PRICE_ONLINE_DELIVERY_MONTHLY = 0;   // Free

    // Annual Prices in Paise
    private static final long PRICE_BASE_ANNUAL = 99900;          // ₹999
    private static final long PRICE_KOT_ANNUAL = 50000;           // ₹500
    private static final long PRICE_INVENTORY_ANNUAL = 199200;    // ₹1992 (₹166/mo * 12)
    private static final long PRICE_CRM_ANNUAL = 99000;           // ₹990 (₹83/mo * 12)
    private static final long PRICE_CREDIT_LEDGER_ANNUAL = 49200;  // ₹492 (₹41/mo * 12)
    private static final long PRICE_TABLE_QR_ANNUAL = 0;          // Free
    private static final long PRICE_MENU_IMAGES_ANNUAL = 0;        // Free
    private static final long PRICE_ONLINE_DELIVERY_ANNUAL = 0;    // Free

    // One-Time Setup Fee
    private static final long PRICE_SETUP_WHITE_GLOVE = 149900;   // ₹1499

    @Transactional(readOnly = true)
    public SubscriptionStatusResponse getStatus(UUID clientId) {
        Client client = findClient(clientId);
        return toStatus(client);
    }

    @Transactional
    public SubscriptionPaymentResponse createPayment(UUID clientId, SubscriptionPaymentRequest request) {
        Client client = findClient(clientId);
        // Force Yearly-Only business billing model
        request.setBillingCycle("ANNUAL");
        long totalAmountPaise = calculateTotalAmount(client, request);

        Map<String, Object> notes = new LinkedHashMap<>();
        notes.put("purpose", "subscription");
        notes.put("client_id", client.getId().toString());
        notes.put("client_name", client.getName() != null ? client.getName() : "");
        notes.put("client_email", client.getEmail() != null ? client.getEmail() : "");
        
        // Metadata for activation
        notes.put("billing_cycle", request.getBillingCycle() != null ? request.getBillingCycle() : "MONTHLY");
        notes.put("setup_option", request.getSetupOption() != null ? request.getSetupOption() : "DIY");
        notes.put("org_id", request.getOrgId() != null ? request.getOrgId().toString() : "");
        
        if (request.getModules() != null && !request.getModules().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < request.getModules().size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(request.getModules().get(i).name());
            }
            notes.put("modules", sb.toString());
        } else {
            notes.put("modules", "");
        }

        String receipt = "sub_" + client.getId().toString().substring(0, 8) + "_" + System.currentTimeMillis();
        RazorpayOrderResponse order = razorpayService.createOrder(totalAmountPaise, "INR", receipt, notes);

        return SubscriptionPaymentResponse.builder()
                .orderId(order.getOrderId())
                .amount(order.getAmount())
                .currency(order.getCurrency())
                .keyId(order.getKeyId())
                .planName("Cafe QR 2.0 Subscription")
                .description("Total Checkout - Rs " + (totalAmountPaise / 100))
                .build();
    }

    long calculateTotalAmount(Client client, SubscriptionPaymentRequest request) {
        boolean isAnnual = "ANNUAL".equalsIgnoreCase(request.getBillingCycle());
        long total = 0;

        // 1. Calculate Base Plan Price
        total += isAnnual ? PRICE_BASE_ANNUAL : PRICE_BASE_MONTHLY;

        // 2. Add Setup Fee if White-Glove
        if ("WHITE_GLOVE".equalsIgnoreCase(request.getSetupOption())) {
            total += PRICE_SETUP_WHITE_GLOVE;
        }

        // 3. Add Sachet Modules
        if (request.getModules() != null) {
            for (ModuleName module : request.getModules()) {
                total += getModulePrice(module, isAnnual);
            }
        }

        return total;
    }

    private long getModulePrice(ModuleName module, boolean isAnnual) {
        switch (module) {
            case KOT:
                return isAnnual ? PRICE_KOT_ANNUAL : PRICE_KOT_MONTHLY;
            case INVENTORY:
                return isAnnual ? PRICE_INVENTORY_ANNUAL : PRICE_INVENTORY_MONTHLY;
            case CRM:
                return isAnnual ? PRICE_CRM_ANNUAL : PRICE_CRM_MONTHLY;
            case CREDIT_LEDGER:
                return isAnnual ? PRICE_CREDIT_LEDGER_ANNUAL : PRICE_CREDIT_LEDGER_MONTHLY;
            case TABLE_QR:
                return isAnnual ? PRICE_TABLE_QR_ANNUAL : PRICE_TABLE_QR_MONTHLY;
            case MENU_IMAGES:
                return isAnnual ? PRICE_MENU_IMAGES_ANNUAL : PRICE_MENU_IMAGES_MONTHLY;
            case ONLINE_DELIVERY:
                return isAnnual ? PRICE_ONLINE_DELIVERY_ANNUAL : PRICE_ONLINE_DELIVERY_MONTHLY;
            default:
                return 0;
        }
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

        // Fetch Razorpay order to extract metadata securely
        com.fasterxml.jackson.databind.JsonNode order = razorpayService.fetchOrder(request.getRazorpayOrderId());
        com.fasterxml.jackson.databind.JsonNode notes = order.path("notes");
        
        String billingCycle = notes.path("billing_cycle").asText("MONTHLY");
        String modulesStr = notes.path("modules").asText("");
        String orgIdStr = notes.path("org_id").asText("");

        activateModulesAndBasePlan(clientId, billingCycle, modulesStr, orgIdStr);

        return toStatus(findClient(clientId));
    }

    @Transactional
    public SubscriptionStatusResponse activateFromWebhook(UUID clientId, com.fasterxml.jackson.databind.JsonNode notes) {
        String billingCycle = notes.path("billing_cycle").asText("MONTHLY");
        String modulesStr = notes.path("modules").asText("");
        String orgIdStr = notes.path("org_id").asText("");

        activateModulesAndBasePlan(clientId, billingCycle, modulesStr, orgIdStr);

        return toStatus(findClient(clientId));
    }

    private void activateModulesAndBasePlan(UUID clientId, String billingCycle, String modulesStr, String orgIdStr) {
        Client client = findClient(clientId);
        
        // 1. Activate/Renew Base Plan
        int monthsToAdd = "ANNUAL".equalsIgnoreCase(billingCycle) ? 12 : 1;
        client.setSubscriptionStatus(ACTIVE);
        client.setSubscriptionExpiryDate(calculateExpiryDate(client.getSubscriptionExpiryDate(), clientId, null, monthsToAdd));
        clientRepository.save(client);

        // 2. Activate/Renew selected sachet modules
        if (modulesStr != null && !modulesStr.isBlank()) {
            UUID orgId = (orgIdStr == null || orgIdStr.isBlank()) ? null : UUID.fromString(orgIdStr);
            String[] mods = modulesStr.split(",");
            for (String mod : mods) {
                if (mod.trim().isEmpty()) continue;
                try {
                    ModuleName moduleName = ModuleName.valueOf(mod.trim());
                    
                    // Retrieve existing module subscription to extend or create a new one
                    ClientSubscriptionModule moduleSub;
                    if (orgId == null) {
                        moduleSub = clientSubscriptionModuleRepository
                                .findByClientIdAndModuleNameAndOrgIdIsNull(clientId, moduleName)
                                .orElse(null);
                    } else {
                        moduleSub = clientSubscriptionModuleRepository
                                .findByClientIdAndOrgIdAndModuleName(clientId, orgId, moduleName)
                                .orElse(null);
                    }

                    LocalDateTime currentExpiry = (moduleSub != null) ? moduleSub.getExpiryDate() : null;
                    LocalDateTime newExpiry = calculateExpiryDate(currentExpiry, clientId, orgId, monthsToAdd);

                    if (moduleSub == null) {
                        moduleSub = ClientSubscriptionModule.builder()
                                .clientId(clientId)
                                .orgId(orgId)
                                .moduleName(moduleName)
                                .status(ACTIVE)
                                .autoRenew(true)
                                .expiryDate(newExpiry)
                                .build();
                    } else {
                        moduleSub.setStatus(ACTIVE);
                        moduleSub.setExpiryDate(newExpiry);
                    }
                    clientSubscriptionModuleRepository.save(moduleSub);
                } catch (IllegalArgumentException e) {
                    // Ignore or log invalid module name
                }
            }
        }
    }

    LocalDateTime calculateExpiryDate(LocalDateTime currentExpiry, UUID clientId, UUID orgId, int monthsToAdd) {
        ZoneId zone = timezoneResolver.resolveTimezone(clientId, orgId);
        ZonedDateTime nowInZone = ZonedDateTime.now(zone);
        
        ZonedDateTime baseZonedDateTime;
        if (currentExpiry != null) {
            ZonedDateTime existingExpiryInZone = currentExpiry
                    .atZone(ZoneId.systemDefault())
                    .withZoneSameInstant(zone);
            if (existingExpiryInZone.isAfter(nowInZone)) {
                baseZonedDateTime = existingExpiryInZone;
            } else {
                baseZonedDateTime = nowInZone;
            }
        } else {
            baseZonedDateTime = nowInZone;
        }
        
        ZonedDateTime newExpiryInZone = baseZonedDateTime.plusMonths(monthsToAdd);
        newExpiryInZone = newExpiryInZone
                .withHour(23)
                .withMinute(59)
                .withSecond(59)
                .withNano(999000000);
                
        return newExpiryInZone
                .withZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime();
    }

    private Client findClient(UUID clientId) {
        if (clientId == null) {
            throw new BusinessException("Client context is missing");
        }
        return clientRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found with id: " + clientId));
    }

    private SubscriptionStatusResponse toStatus(Client client) {
        ZoneId zone = timezoneResolver.resolveTimezone(client.getId(), null);
        ZonedDateTime nowInZone = ZonedDateTime.now(zone);
        
        String status = normalizeStatus(client.getSubscriptionStatus());
        LocalDateTime expiry = client.getSubscriptionExpiryDate();
        ZonedDateTime expiryInZone = expiry != null ? expiry.atZone(ZoneId.systemDefault()).withZoneSameInstant(zone) : null;
        
        boolean active = (ACTIVE.equals(status) || TRIAL.equals(status)) && expiryInZone != null && !expiryInZone.isBefore(nowInZone);
        int daysLeft = active ? Math.max(0, (int) Math.ceil(Duration.between(nowInZone, expiryInZone).toHours() / 24.0)) : 0;

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
