package com.restaurant.pos.delivery.command;

import com.restaurant.pos.accounting.service.AccountingPostingService;
import com.restaurant.pos.client.domain.Client;
import com.restaurant.pos.client.domain.Organization;
import com.restaurant.pos.client.repository.ClientRepository;
import com.restaurant.pos.client.repository.OrganizationRepository;
import com.restaurant.pos.common.dto.ConfigurationDto;
import com.restaurant.pos.common.exception.BusinessException;
import com.restaurant.pos.common.service.SystemConfigurationService;
import com.restaurant.pos.delivery.event.DeliveryOrderPlacedEvent;
import com.restaurant.pos.invoice.domain.Invoice;
import com.restaurant.pos.order.domain.Order;
import com.restaurant.pos.order.domain.OrderLine;
import com.restaurant.pos.order.domain.OrderType;
import com.restaurant.pos.order.domain.TaxType;
import com.restaurant.pos.order.repository.OrderRepository;
import com.restaurant.pos.order.service.OrderService;
import com.restaurant.pos.payment.dto.RazorpayOrderResponse;
import com.restaurant.pos.payment.service.RazorpayService;
import com.restaurant.pos.print.domain.PrintJobKind;
import com.restaurant.pos.print.service.PrintJobService;
import com.restaurant.pos.product.domain.Product;
import com.restaurant.pos.product.repository.ProductRepository;
import com.restaurant.pos.push.service.PushNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryCommandService {

    private final ClientRepository clientRepository;
    private final OrganizationRepository organizationRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final SystemConfigurationService systemConfigurationService;
    private final PrintJobService printJobService;
    private final PushNotificationService pushNotificationService;
    private final RazorpayService razorpayService;
    private final ApplicationEventPublisher eventPublisher;
    private final OrderService orderService;
    private final AccountingPostingService accountingPostingService;

    @Transactional
    public Map<String, Object> createPaymentOrder(CreateDeliveryPaymentCommand command) {
        UUID clientId = command.getClientId();
        validateSubscription(clientId);

        UUID orgUuid = parseOrgId(command.getOrgId());
        var clientOpt = clientRepository.findById(clientId);
        if (clientOpt.isEmpty()) {
            var orgOpt = organizationRepository.findById(clientId);
            if (orgOpt.isPresent()) {
                clientId = orgOpt.get().getClientId();
                if (orgUuid == null) {
                    orgUuid = orgOpt.get().getId();
                }
            }
        }

        ConfigurationDto config = systemConfigurationService.getConfigurationForClientAndBranch(clientId, orgUuid);

        if (!config.isOnlinePaymentEnabled() || config.getRazorpayKeyId() == null || config.getRazorpayKeyId().isBlank()
                || config.getRazorpayKeySecret() == null || config.getRazorpayKeySecret().isBlank()) {
            throw new BusinessException("Online payment is not configured or enabled for this restaurant.");
        }

        List<Map<String, Object>> items = command.getItems();
        if (items == null || items.isEmpty()) {
            throw new BusinessException("No items provided for payment calculation.");
        }

        BigDecimal grandTotal = calculateOrderTotal(clientId, orgUuid, items, config);

        Map<String, Object> notes = new LinkedHashMap<>();
        notes.put("clientId", clientId.toString());
        if (orgUuid != null) notes.put("orgId", orgUuid.toString());
        if (command.getCustomerPhone() != null) notes.put("customerPhone", command.getCustomerPhone());
        if (command.getCustomerEmail() != null) notes.put("customerEmail", command.getCustomerEmail());

        RazorpayOrderResponse rzpOrder = razorpayService.createOrderWithKeys(
                config.getRazorpayKeyId(),
                config.getRazorpayKeySecret(),
                grandTotal,
                "INR",
                "del_" + System.currentTimeMillis(),
                notes
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("razorpayOrderId", rzpOrder.getOrderId());
        response.put("keyId",           rzpOrder.getKeyId());
        response.put("amount",          rzpOrder.getAmount());
        response.put("currency",        rzpOrder.getCurrency());
        response.put("grandTotal",      grandTotal);

        return response;
    }

    @Transactional
    public Map<String, Object> placeOrder(CreateDeliveryOrderCommand command) {
        try {
            UUID clientId = command.getClientId();
            validateSubscription(clientId);

            UUID orgUuid = parseOrgId(command.getOrgId());
            var clientOpt = clientRepository.findById(clientId);
            if (clientOpt.isEmpty()) {
                var orgOpt = organizationRepository.findById(clientId);
                if (orgOpt.isPresent()) {
                    clientId = orgOpt.get().getClientId();
                    if (orgUuid == null) {
                        orgUuid = orgOpt.get().getId();
                    }
                }
            }
            final UUID effectiveClientId = clientId;
            final UUID effectiveOrgId = orgUuid;

            String fulfillment    = command.getFulfillmentType() != null ? command.getFulfillmentType().toUpperCase() : "DELIVERY";
            String customerEmail  = command.getCustomerEmail() != null ? command.getCustomerEmail() : "";
            String customerName   = command.getCustomerName() != null ? command.getCustomerName() : "";
            String customerPhone  = command.getCustomerPhone() != null ? command.getCustomerPhone() : "";
            String deliveryAddress= command.getDeliveryAddress() != null ? command.getDeliveryAddress() : "";
            String note           = command.getNote() != null ? command.getNote() : "";
            String remarks        = command.getRemarks() != null ? command.getRemarks() : "";

            String paymentMethod     = command.getPaymentMethod() != null ? command.getPaymentMethod().toUpperCase() : "COD";
            String razorpayPaymentId = command.getRazorpayPaymentId();
            String razorpayOrderId   = command.getRazorpayOrderId();
            String razorpaySignature = command.getRazorpaySignature();

            List<Map<String, Object>> items = command.getItems();
            if (items == null || items.isEmpty()) {
                throw new BusinessException("No items in order");
            }

            ConfigurationDto config = null;
            try {
                config = systemConfigurationService.getConfigurationForClientAndBranch(clientId, orgUuid);
            } catch (Exception e) {
                log.warn("[Delivery] Failed to fetch system configuration, using fallback defaults", e);
            }

            boolean isOnlinePayment = "ONLINE".equals(paymentMethod) || "RAZORPAY".equals(paymentMethod);
            if (isOnlinePayment) {
                if (config == null || config.getRazorpayKeySecret() == null || config.getRazorpayKeySecret().isBlank()) {
                    throw new BusinessException("Payment gateway is not configured for this restaurant.");
                }
                boolean valid = razorpayService.verifyPaymentSignatureWithSecret(
                        razorpayOrderId, razorpayPaymentId, razorpaySignature, config.getRazorpayKeySecret());
                if (!valid) {
                    throw new BusinessException("Payment signature verification failed. Order not confirmed.");
                }
            }

            String orderNo = "DEL-" + System.currentTimeMillis();
            String description = buildDescription(customerEmail, customerName, customerPhone, deliveryAddress, note);

            BigDecimal latitude = command.getLatitude();
            BigDecimal longitude = command.getLongitude();

            if ("DELIVERY".equalsIgnoreCase(fulfillment) && orgUuid != null && latitude != null && longitude != null) {
                final double customerLat = latitude.doubleValue();
                final double customerLng = longitude.doubleValue();
                organizationRepository.findById(orgUuid).ifPresent(org -> {
                    if (org.getDeliveryRadiusKm() != null && org.getDeliveryRadiusKm() > 0
                            && org.getLatitude() != null && org.getLongitude() != null) {
                        double distKm = haversineDistanceKm(
                                org.getLatitude(), org.getLongitude(),
                                customerLat, customerLng);
                        if (distKm > org.getDeliveryRadiusKm()) {
                            throw new BusinessException(String.format(
                                    "Your location is %.1f km away. Delivery is only available within %.0f km of the restaurant.",
                                    distKm, org.getDeliveryRadiusKm()));
                        }
                    }
                });
            }

            Order order = Order.builder()
                    .id(UUID.randomUUID())
                    .orderNo(orderNo)
                    .orderType(OrderType.SALE)
                    .orderStatus("PENDING")
                    .paymentStatus(isOnlinePayment ? "PAID" : "PENDING")
                    .orderSource("DELIVERY_WEB")
                    .fulfillmentType(fulfillment)
                    .description(description)
                    .remarks(remarks)
                    .reference(isOnlinePayment ? ("RAZORPAY:" + razorpayPaymentId) : "COD")
                    .orderDate(Instant.now())
                    .isactive("Y")
                    .build();

            order.setClientId(clientId);
            order.setOrgId(orgUuid);
            order.setLatitude(latitude);
            order.setLongitude(longitude);

            boolean gstEnabled = config != null && config.isTaxEnabled();
            boolean pricesIncludeTax = config != null && config.isPricesIncludeTax();
            int decimalPlaces = (config != null && config.getCurrencyDecimalPlaces() != null) ? config.getCurrencyDecimalPlaces() : 2;

            BigDecimal baseRate = BigDecimal.ZERO;
            List<Map<String, Object>> taxRatesList = new ArrayList<>();
            String defaultTaxId = config != null ? config.getTaxDefaultId() : null;

            if (config != null && config.getTaxRates() != null) {
                for (Object rateObj : config.getTaxRates()) {
                    if (rateObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> rateMap = (Map<String, Object>) rateObj;
                        taxRatesList.add(rateMap);
                    }
                }
            }

            if (gstEnabled && !taxRatesList.isEmpty()) {
                Map<String, Object> defaultRateMap = null;
                if (defaultTaxId != null) {
                    defaultRateMap = taxRatesList.stream()
                            .filter(r -> defaultTaxId.equals(String.valueOf(r.get("id"))))
                            .findFirst().orElse(null);
                }
                if (defaultRateMap == null) {
                    defaultRateMap = taxRatesList.get(0);
                }
                if (defaultRateMap != null && defaultRateMap.get("value") != null) {
                    try {
                        baseRate = new BigDecimal(String.valueOf(defaultRateMap.get("value")));
                    } catch (Exception ignored) {}
                }
            }

            BigDecimal totalTaxableAmount = BigDecimal.ZERO;
            BigDecimal totalTaxAmount = BigDecimal.ZERO;
            BigDecimal totalGrossAmount = BigDecimal.ZERO;
            BigDecimal grandTotal = BigDecimal.ZERO;

            for (Map<String, Object> cartItem : items) {
                UUID productId = UUID.fromString((String) cartItem.get("productId"));
                int qty = ((Number) cartItem.get("quantity")).intValue();

                Optional<Product> productOpt = productRepository.findWithCategoryById(productId)
                        .filter(p -> effectiveClientId.equals(p.getClientId()))
                        .filter(p -> effectiveOrgId == null || p.getOrgId() == null || effectiveOrgId.equals(p.getOrgId()))
                        .filter(Product::isActive)
                        .filter(Product::isAvailable);

                if (productOpt.isEmpty()) {
                    throw new BusinessException("Invalid or unavailable item: " + productId);
                }

                Product p = productOpt.get();
                BigDecimal faceUnit = p.getPrice();
                String lineProductName = p.getName();
                if (cartItem.get("variantName") != null && !String.valueOf(cartItem.get("variantName")).isBlank()) {
                    lineProductName = p.getName() + " (" + String.valueOf(cartItem.get("variantName")).trim() + ")";
                } else if (cartItem.get("variant_name") != null && !String.valueOf(cartItem.get("variant_name")).isBlank()) {
                    lineProductName = p.getName() + " (" + String.valueOf(cartItem.get("variant_name")).trim() + ")";
                }
                if (cartItem.get("variantPrice") != null) {
                    try { faceUnit = new BigDecimal(String.valueOf(cartItem.get("variantPrice"))); } catch (Exception ignored) {}
                } else if (cartItem.get("price") != null) {
                    try { faceUnit = new BigDecimal(String.valueOf(cartItem.get("price"))); } catch (Exception ignored) {}
                }

                UUID variantUuid = null;
                if (cartItem.get("variantId") != null) {
                    try { variantUuid = UUID.fromString(String.valueOf(cartItem.get("variantId"))); } catch (Exception ignored) {}
                } else if (cartItem.get("variant_id") != null) {
                    try { variantUuid = UUID.fromString(String.valueOf(cartItem.get("variant_id"))); } catch (Exception ignored) {}
                }

                BigDecimal quantity = BigDecimal.valueOf(qty);
                BigDecimal grossLineAmount = faceUnit.multiply(quantity);

                boolean isPackaged = p.isPackagedGood();
                BigDecimal rate = BigDecimal.ZERO;
                if (gstEnabled) {
                    if (isPackaged) {
                        rate = p.getTaxRate() != null ? p.getTaxRate() : baseRate;
                    } else {
                        rate = baseRate;
                    }
                }

                boolean isInclusive = gstEnabled && (isPackaged || pricesIncludeTax);

                BigDecimal baseUnit;
                BigDecimal lineTotal;
                BigDecimal taxable;
                BigDecimal tax;

                if (isInclusive && rate.compareTo(BigDecimal.ZERO) > 0) {
                    baseUnit = faceUnit.divide(BigDecimal.ONE.add(rate.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)), 10, RoundingMode.HALF_UP);
                    lineTotal = grossLineAmount.setScale(decimalPlaces, RoundingMode.HALF_UP);
                    taxable = lineTotal.divide(BigDecimal.ONE.add(rate.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)), decimalPlaces, RoundingMode.HALF_UP);
                    tax = lineTotal.subtract(taxable);
                } else {
                    baseUnit = faceUnit;
                    taxable = grossLineAmount.setScale(decimalPlaces, RoundingMode.HALF_UP);
                    tax = taxable.multiply(rate.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)).setScale(decimalPlaces, RoundingMode.HALF_UP);
                    lineTotal = taxable.add(tax);
                }

                String taxCode = null;
                String taxName = null;
                if (gstEnabled && rate.compareTo(BigDecimal.ZERO) > 0) {
                    final BigDecimal finalRate = rate;
                    Map<String, Object> matchedRate = taxRatesList.stream()
                            .filter(r -> {
                                try {
                                    return new BigDecimal(String.valueOf(r.get("value"))).compareTo(finalRate) == 0;
                                } catch (Exception e) {
                                    return false;
                                }
                            })
                            .findFirst().orElse(null);

                    if (matchedRate != null) {
                        taxCode = (String) matchedRate.get("code");
                        taxName = (String) matchedRate.get("name");
                    }
                    if (taxCode == null) {
                        taxCode = "GST_" + rate.toPlainString();
                    }
                    if (taxName == null) {
                        taxName = "GST " + rate.toPlainString() + "%";
                    }
                }

                TaxType taxType = isInclusive ? TaxType.INCLUSIVE : (gstEnabled && rate.compareTo(BigDecimal.ZERO) > 0 ? TaxType.EXCLUSIVE : TaxType.NONE);

                OrderLine line = OrderLine.builder()
                        .productId(productId)
                        .productName(lineProductName)
                        .variantId(variantUuid)
                        .categoryName(p.getCategory() != null ? p.getCategory().getName() : null)
                        .isPackagedGood(isPackaged)
                        .quantity(quantity)
                        .unitOfMeasure(p.getUom() != null ? p.getUom().getName() : "units")
                        .unitPrice(faceUnit)
                        .taxRate(rate)
                        .taxAmount(tax)
                        .discountAmount(BigDecimal.ZERO)
                        .lineTotal(lineTotal)
                        .grossLineAmount(grossLineAmount)
                        .unitPriceExTax(baseUnit.setScale(4, RoundingMode.HALF_UP))
                        .taxableAmount(taxable)
                        .taxType(taxType)
                        .taxSnapshotRate(rate)
                        .taxCode(taxCode)
                        .taxName(taxName)
                        .allocatedOrderDiscount(BigDecimal.ZERO)
                        .isactive("Y")
                        .build();

                order.addLine(line);

                totalTaxableAmount = totalTaxableAmount.add(taxable);
                totalTaxAmount = totalTaxAmount.add(tax);
                totalGrossAmount = totalGrossAmount.add(grossLineAmount);
                grandTotal = grandTotal.add(lineTotal);
            }

            order.setGrossAmount(totalGrossAmount);
            order.setTotalTaxAmount(totalTaxAmount);
            order.setTotalDiscountAmount(BigDecimal.ZERO);
            order.setTotalAmount(grandTotal);
            order.setGrandTotal(grandTotal);

            Order saved = orderRepository.save(order);
            log.info("[DeliveryCommandService] Order placed: {} (orderNo={}) for client={} org={}",
                    saved.getId(), saved.getOrderNo(), clientId, orgUuid);

            // ── Auto-settle online-paid delivery orders ──
            if (isOnlinePayment) {
                try {
                    saved.setOrderStatus("COMPLETED");
                    saved.setPaymentStatus("PAID");
                    saved = orderRepository.save(saved);

                    Invoice invoice = orderService.generateInvoice(saved);
                    if (invoice != null) {
                        invoice.setStatus("PAID");
                        invoice.setAmountDue(BigDecimal.ZERO);
                    }
                    orderService.generatePayment(saved, "ONLINE", null,
                            saved.getGrandTotal(), "Auto-settled: Delivery online payment (RAZORPAY:" + razorpayPaymentId + ")");
                    accountingPostingService.postSaleCogs(saved);
                    log.info("[DeliveryCommandService] Online-paid order auto-settled: {} invoice+payment created", saved.getId());
                } catch (Exception ex) {
                    log.error("[DeliveryCommandService] Failed to auto-settle online-paid order {}", saved.getId(), ex);
                }
            }

            try {
                pushNotificationService.sendNewOrderPush(saved);
            } catch (Exception ex) {
                log.error("[DeliveryCommandService] Failed to trigger push notification", ex);
            }

            if (isKitchenPrintStatus(saved.getOrderStatus())) {
                try {
                    printJobService.enqueueForOrder(saved, PrintJobKind.KOT, "auto");
                } catch (Exception ex) {
                    log.warn("[DeliveryCommandService] Unable to enqueue print job for order {}", saved.getId(), ex);
                }
            }

            // Publish Domain Event
            eventPublisher.publishEvent(new DeliveryOrderPlacedEvent(this, saved));

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("orderId",         saved.getId());
            response.put("orderNo",         saved.getOrderNo());
            response.put("status",          saved.getOrderStatus());
            response.put("paymentStatus",   saved.getPaymentStatus());
            response.put("fulfillmentType", saved.getFulfillmentType());
            response.put("grandTotal",      saved.getGrandTotal());

            return response;
        } catch (Exception ex) {
            log.error("[DeliveryCommandService] Failed to place order: {}", ex.getMessage(), ex);
            throw ex;
        }
    }

    private void validateSubscription(UUID clientId) {
        if (clientId == null) {
            throw new BusinessException("Client ID is required");
        }
        var clientOpt = clientRepository.findById(clientId);
        if (clientOpt.isPresent()) {
            Client client = clientOpt.get();
            if (!"ACTIVE".equalsIgnoreCase(client.getSubscriptionStatus()) && !"TRIAL".equalsIgnoreCase(client.getSubscriptionStatus())) {
                throw new BusinessException("Restaurant subscription is inactive.");
            }
        }
    }

    private UUID parseOrgId(String orgIdStr) {
        if (orgIdStr == null || orgIdStr.isBlank() || "null".equalsIgnoreCase(orgIdStr)) {
            return null;
        }
        try {
            return UUID.fromString(orgIdStr.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String buildDescription(String email, String name, String phone, String address, String note) {
        StringBuilder sb = new StringBuilder();
        if (!name.isBlank())    sb.append("Cust: ").append(name).append(" | ");
        if (!email.isBlank())   sb.append("Email: ").append(email).append(" | ");
        if (!phone.isBlank())   sb.append("Phone: ").append(phone).append(" | ");
        if (!address.isBlank()) sb.append("Addr: ").append(address).append(" | ");
        if (!note.isBlank())    sb.append("Note: ").append(note).append(" | ");

        String res = sb.toString();
        if (res.endsWith(" | ")) {
            res = res.substring(0, res.length() - 3);
        }
        return res;
    }

    private BigDecimal calculateOrderTotal(UUID clientId, UUID orgUuid, List<Map<String, Object>> items, ConfigurationDto config) {
        boolean gstEnabled = config != null && config.isTaxEnabled();
        boolean pricesIncludeTax = config != null && config.isPricesIncludeTax();
        int decimalPlaces = (config != null && config.getCurrencyDecimalPlaces() != null) ? config.getCurrencyDecimalPlaces() : 2;

        BigDecimal baseRate = BigDecimal.ZERO;
        List<Map<String, Object>> taxRatesList = new ArrayList<>();
        String defaultTaxId = config != null ? config.getTaxDefaultId() : null;

        if (config != null && config.getTaxRates() != null) {
            for (Object rateObj : config.getTaxRates()) {
                if (rateObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> rateMap = (Map<String, Object>) rateObj;
                    taxRatesList.add(rateMap);
                }
            }
        }

        if (gstEnabled && !taxRatesList.isEmpty()) {
            Map<String, Object> defaultRateMap = null;
            if (defaultTaxId != null) {
                defaultRateMap = taxRatesList.stream()
                        .filter(r -> defaultTaxId.equals(String.valueOf(r.get("id"))))
                        .findFirst().orElse(null);
            }
            if (defaultRateMap == null) {
                defaultRateMap = taxRatesList.get(0);
            }
            if (defaultRateMap != null && defaultRateMap.get("value") != null) {
                try {
                    baseRate = new BigDecimal(String.valueOf(defaultRateMap.get("value")));
                } catch (Exception ignored) {}
            }
        }

        BigDecimal grandTotal = BigDecimal.ZERO;
        for (Map<String, Object> cartItem : items) {
            UUID productId = UUID.fromString((String) cartItem.get("productId"));
            int qty = ((Number) cartItem.get("quantity")).intValue();

            Optional<Product> productOpt = productRepository.findWithCategoryById(productId)
                    .filter(p -> clientId.equals(p.getClientId()))
                    .filter(p -> orgUuid == null || p.getOrgId() == null || orgUuid.equals(p.getOrgId()))
                    .filter(Product::isActive)
                    .filter(Product::isAvailable);

            if (productOpt.isEmpty()) {
                throw new BusinessException("Invalid or unavailable item: " + productId);
            }

            Product p = productOpt.get();
            BigDecimal faceUnit = p.getPrice();
            if (cartItem.get("variantPrice") != null) {
                try { faceUnit = new BigDecimal(String.valueOf(cartItem.get("variantPrice"))); } catch (Exception ignored) {}
            } else if (cartItem.get("price") != null) {
                try { faceUnit = new BigDecimal(String.valueOf(cartItem.get("price"))); } catch (Exception ignored) {}
            }

            BigDecimal quantity = BigDecimal.valueOf(qty);
            BigDecimal grossLineAmount = faceUnit.multiply(quantity);

            boolean isPackaged = p.isPackagedGood();
            BigDecimal rate = BigDecimal.ZERO;
            if (gstEnabled) {
                rate = isPackaged ? (p.getTaxRate() != null ? p.getTaxRate() : baseRate) : baseRate;
            }

            boolean isInclusive = gstEnabled && (isPackaged || pricesIncludeTax);
            BigDecimal lineTotal;

            if (isInclusive && rate.compareTo(BigDecimal.ZERO) > 0) {
                lineTotal = grossLineAmount.setScale(decimalPlaces, RoundingMode.HALF_UP);
            } else {
                BigDecimal taxable = grossLineAmount.setScale(decimalPlaces, RoundingMode.HALF_UP);
                BigDecimal tax = taxable.multiply(rate.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)).setScale(decimalPlaces, RoundingMode.HALF_UP);
                lineTotal = taxable.add(tax);
            }

            grandTotal = grandTotal.add(lineTotal);
        }
        return grandTotal;
    }

    private boolean isKitchenPrintStatus(String status) {
        return "CONFIRMED".equalsIgnoreCase(status)
                || "IN_PROGRESS".equalsIgnoreCase(status)
                || "KITCHEN".equalsIgnoreCase(status)
                || "PENDING".equalsIgnoreCase(status);
    }

    private double haversineDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Radius of the earth in km
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
