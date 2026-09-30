package com.restaurant.pos.qrmenu.service;

import com.restaurant.pos.client.domain.Client;
import com.restaurant.pos.client.domain.Organization;
import com.restaurant.pos.client.repository.ClientRepository;
import com.restaurant.pos.client.repository.OrganizationRepository;
import com.restaurant.pos.common.exception.BusinessException;
import com.restaurant.pos.common.service.SystemConfigurationService;
import com.restaurant.pos.order.domain.Order;
import com.restaurant.pos.order.domain.OrderLine;
import com.restaurant.pos.order.domain.OrderType;
import com.restaurant.pos.order.repository.OrderRepository;
import com.restaurant.pos.print.domain.PrintJobKind;
import com.restaurant.pos.print.service.PrintJobService;
import com.restaurant.pos.product.domain.Product;
import com.restaurant.pos.product.repository.ProductRepository;
import com.restaurant.pos.purchasing.domain.Customer;
import com.restaurant.pos.purchasing.repository.CustomerRepository;
import com.restaurant.pos.qrmenu.repository.QrOrderRepository;
import com.restaurant.pos.sequence.domain.DocumentType;
import com.restaurant.pos.sequence.service.DocumentSequenceService;
import com.restaurant.pos.subscription.domain.ModuleName;
import com.restaurant.pos.table.domain.RestaurantTable;
import com.restaurant.pos.table.repository.RestaurantTableRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Isolated Domain Service handling all QR Code Table Sessions and Order Appending logic.
 * Integrates DocumentSequenceService to generate official sequence numbers (e.g. SO-2026-0000220-HQ).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QrTableSessionService {

    private final ProductRepository productRepository;
    private final RestaurantTableRepository tableRepository;
    private final OrderRepository orderRepository;
    private final QrOrderRepository qrOrderRepository;
    private final ClientRepository clientRepository;
    private final OrganizationRepository organizationRepository;
    private final SystemConfigurationService systemConfigurationService;
    private final CustomerRepository customerRepository;
    private final PrintJobService printJobService;
    private final DocumentSequenceService documentSequenceService;

    /**
     * Resolves client UUID from either a raw UUID string or a human-readable slug string.
     */
    public UUID resolveClientId(String identifier) {
        if (identifier == null || identifier.isBlank() || "null".equalsIgnoreCase(identifier)) {
            return null;
        }
        try {
            return UUID.fromString(identifier);
        } catch (IllegalArgumentException ex) {
            return clientRepository.findBySlugIgnoreCase(identifier)
                    .map(Client::getId)
                    .orElseThrow(() -> new BusinessException("Client not found for identifier: " + identifier));
        }
    }

    /**
     * Resolves organization UUID from either a raw UUID string or a human-readable slug / branch code.
     */
    public UUID resolveOrgId(UUID clientId, String identifier) {
        if (identifier == null || identifier.isBlank() || "null".equalsIgnoreCase(identifier)) {
            return null;
        }
        try {
            return UUID.fromString(identifier);
        } catch (IllegalArgumentException ex) {
            if (clientId == null) return null;
            return organizationRepository.findByClientIdAndSlugIgnoreCase(clientId, identifier)
                    .or(() -> organizationRepository.findByClientIdAndBranchCodeIgnoreCase(clientId, identifier))
                    .map(Organization::getId)
                    .orElse(null);
        }
    }

    /**
     * Retrieves table metadata along with any active open order (Table Session) on that table.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getTableSessionInfo(String clientIdentifier, String orgIdentifier, String tableIdentifier) {
        UUID clientId = resolveClientId(clientIdentifier);
        UUID orgUuid = resolveOrgId(clientId, orgIdentifier);

        validateSubscription(clientId, orgUuid);

        RestaurantTable table = findTable(clientId, orgUuid, tableIdentifier);
        if (table == null) {
            return Map.of(
                    "found", false,
                    "message", "Table not found"
            );
        }

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("found", true);
        info.put("id", table.getId());
        info.put("tableNumber", table.getTableNumber());
        info.put("name", table.getName());
        info.put("status", table.getStatus());
        info.put("seatingCapacity", table.getSeatingCapacity());
        info.put("floor", table.getFloor());
        info.put("section", table.getSection());

        UUID effectiveClientId = table.getClientId() != null ? table.getClientId() : clientId;
        UUID effectiveOrgId = table.getOrgId() != null ? table.getOrgId() : orgUuid;

        // Online payment mirrors Delivery Website pattern (DeliveryQueryService:L269-272):
        // requires onlineDeliveryEnabled + onlinePaymentEnabled + valid Razorpay key
        com.restaurant.pos.common.dto.ConfigurationDto qrConfig = systemConfigurationService
                .getConfigurationForClientAndBranch(effectiveClientId, effectiveOrgId);
        boolean onlinePayActive = qrConfig.isOnlinePaymentEnabled()
                && qrConfig.isOnlineDeliveryEnabled()
                && qrConfig.getRazorpayKeyId() != null
                && !qrConfig.getRazorpayKeyId().isBlank();
        info.put("onlinePaymentEnabled", onlinePayActive);

        clientRepository.findById(effectiveClientId).ifPresent(client -> {
            info.put("brandColor", client.getBrandColor() != null ? client.getBrandColor() : "#f97316");
            info.put("restaurantName", client.getName() != null ? client.getName() : "Our Restaurant");
            info.put("logoUrl", client.getLogoUrl());
            info.put("clientSlug", client.getSlug());
        });

        if (effectiveOrgId != null) {
            organizationRepository.findById(effectiveOrgId).ifPresent(org -> {
                if (org.getLogoUrl() != null && !org.getLogoUrl().isBlank()) {
                    info.put("logoUrl", org.getLogoUrl());
                }
                if (org.getName() != null && !org.getName().isBlank()) {
                    info.put("restaurantName", org.getName());
                }
                info.put("branchSlug", org.getSlug() != null ? org.getSlug() : org.getBranchCode());
            });
        }

        // Check for active un-settled order on this table
        List<Order> activeOrders = qrOrderRepository.findActiveOrdersByTable(clientId, orgUuid, table.getId(), table.getTableNumber());
        if (!activeOrders.isEmpty()) {
            Order activeOrder = activeOrders.get(0);
            Map<String, Object> activeOrderMap = new LinkedHashMap<>();
            activeOrderMap.put("id", activeOrder.getId());
            activeOrderMap.put("orderNo", activeOrder.getOrderNo());
            activeOrderMap.put("orderStatus", activeOrder.getOrderStatus());
            activeOrderMap.put("paymentStatus", activeOrder.getPaymentStatus());
            activeOrderMap.put("grandTotal", activeOrder.getGrandTotal());
            activeOrderMap.put("createdAt", activeOrder.getCreatedAt());

            List<Map<String, Object>> linesList = new ArrayList<>();
            if (activeOrder.getLines() != null) {
                for (OrderLine line : activeOrder.getLines()) {
                    Map<String, Object> lm = new LinkedHashMap<>();
                    lm.put("id", line.getId());
                    lm.put("productId", line.getProductId());
                    lm.put("productName", line.getProductName());
                    lm.put("categoryName", line.getCategoryName());
                    lm.put("quantity", line.getQuantity());
                    lm.put("unitPrice", line.getUnitPrice());
                    lm.put("lineTotal", line.getLineTotal());
                    linesList.add(lm);
                }
            }
            activeOrderMap.put("lines", linesList);
            info.put("activeOrder", activeOrderMap);
        }

        return info;
    }

    /**
     * Processes QR order placement.
     * Merges into active table order if exists, otherwise creates a new order.
     */
    @Transactional
    public Map<String, Object> processOrder(String clientIdentifier, String orgIdentifier, Map<String, Object> payload) {
        UUID clientId = resolveClientId(clientIdentifier);
        UUID orgUuid = resolveOrgId(clientId, orgIdentifier);

        validateSubscription(clientId, orgUuid);

        String tableNumber = (String) payload.getOrDefault("tableNumber", "QR");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) payload.get("items");
        String customerNote = (String) payload.getOrDefault("note", "");
        String paymentStatus = String.valueOf(payload.getOrDefault("paymentStatus", "PENDING")).toUpperCase();
        String paymentMethod = String.valueOf(payload.getOrDefault("paymentMethod", "CASH")).toUpperCase();
        String razorpayPaymentId = (String) payload.getOrDefault("razorpayPaymentId", null);
        String razorpayOrderId = (String) payload.getOrDefault("razorpayOrderId", null);

        if (items == null || items.isEmpty()) {
            throw new BusinessException("No items in order");
        }

        String tableIdStr = (String) payload.get("tableId");
        RestaurantTable table = findTable(clientId, orgUuid, tableIdStr != null ? tableIdStr : tableNumber);
        UUID tableId = table != null ? table.getId() : null;
        if (table != null && table.getTableNumber() != null) {
            tableNumber = table.getTableNumber();
        }

        // Check if an active open order exists on this table
        Order targetOrder = null;
        boolean isAppended = false;
        if (tableId != null) {
            List<Order> activeOrders = qrOrderRepository.findActiveOrdersByTable(clientId, orgUuid, tableId, tableNumber);
            if (!activeOrders.isEmpty()) {
                targetOrder = activeOrders.get(0);
                isAppended = true;
            }
        }

        List<OrderLine> newLines = new ArrayList<>();
        BigDecimal addedTotal = BigDecimal.ZERO;

        for (Map<String, Object> cartItem : items) {
            UUID productId = UUID.fromString((String) cartItem.get("productId"));
            int qty = ((Number) cartItem.get("quantity")).intValue();
            Optional<Product> productOpt = productRepository.findWithCategoryById(productId)
                    .filter(product -> clientId.equals(product.getClientId()))
                    .filter(product -> orgUuid == null || product.getOrgId() == null || new UUID(0L, 0L).equals(product.getOrgId()) || orgUuid.equals(product.getOrgId()))
                    .filter(Product::isActive)
                    .filter(Product::isAvailable);

            if (productOpt.isEmpty()) {
                throw new BusinessException("Invalid menu item in order");
            }

            Product product = productOpt.get();
            BigDecimal price = product.getPrice();
            BigDecimal lineTotal = price.multiply(BigDecimal.valueOf(qty));
            String productName = product.getName() != null ? product.getName() : (String) cartItem.getOrDefault("name", null);
            String categoryName = product.getCategory() != null
                    ? product.getCategory().getName()
                    : (String) cartItem.getOrDefault("category", null);

            OrderLine line = OrderLine.builder()
                    .productId(productId)
                    .productName(productName)
                    .categoryName(categoryName)
                    .isPackagedGood(product.isPackagedGood())
                    .quantity(BigDecimal.valueOf(qty))
                    .unitPrice(price)
                    .lineTotal(lineTotal)
                    .isactive("Y")
                    .build();

            newLines.add(line);
            addedTotal = addedTotal.add(lineTotal);
        }

        String customerIdStr = (String) payload.get("customerId");
        UUID customerId = (customerIdStr != null && !customerIdStr.isBlank()) ? UUID.fromString(customerIdStr) : null;

        Order saved;
        if (isAppended && targetOrder != null) {
            for (OrderLine newLine : newLines) {
                targetOrder.addLine(newLine);
            }

            BigDecimal currentTotal = targetOrder.getGrandTotal() != null ? targetOrder.getGrandTotal() : BigDecimal.ZERO;
            BigDecimal newGrandTotal = currentTotal.add(addedTotal);
            targetOrder.setTotalAmount(newGrandTotal);
            targetOrder.setGrandTotal(newGrandTotal);
            targetOrder.setUpdatedAt(LocalDateTime.now());

            if (customerNote != null && !customerNote.isBlank()) {
                String existingDesc = targetOrder.getDescription() != null ? targetOrder.getDescription() : "";
                targetOrder.setDescription(existingDesc.isBlank() ? customerNote : existingDesc + " | Addon: " + customerNote);
            }

            if (customerId != null) {
                targetOrder.setCustomerId(customerId);
            }

            saved = orderRepository.save(targetOrder);

            try {
                printJobService.enqueueKotEditJob(saved, newLines, null, "qr_append");
            } catch (Exception ex) {
                log.warn("Failed to enqueue KOT print job for appended QR order: {}", ex.getMessage());
            }

        } else {
            // Generate official order number using DocumentSequenceService (SALE_ORDER sequence)
            String orderNo;
            try {
                orderNo = documentSequenceService.generateNextSequence(DocumentType.SALE_ORDER, orgUuid);
            } catch (Exception ex) {
                log.warn("Failed to generate official sequence for SALE_ORDER, using fallback: {}", ex.getMessage());
                orderNo = "QR-" + System.currentTimeMillis();
            }

            Order order = Order.builder()
                    .id(UUID.randomUUID())
                    .orderNo(orderNo)
                    .orderType(OrderType.SALE)
                    .orderStatus("CONFIRMED")
                    .paymentStatus("PAID".equals(paymentStatus) ? "PAID" : "PENDING")
                    .orderSource("QR_MENU")
                    .fulfillmentType("DINE_IN")
                    .tableNumber(tableNumber)
                    .description(customerNote)
                    .reference(buildPaymentReference(paymentMethod, razorpayPaymentId, razorpayOrderId))
                    .orderDate(Instant.now())
                    .isactive("Y")
                    .build();

            if (tableId != null) {
                order.setTableId(tableId);
            }
            if (customerId != null) {
                order.setCustomerId(customerId);
            }
            order.setClientId(clientId);
            order.setOrgId(orgUuid);

            for (OrderLine newLine : newLines) {
                order.addLine(newLine);
            }

            order.setTotalAmount(addedTotal);
            order.setGrandTotal(addedTotal);

            saved = orderRepository.save(order);

            try {
                printJobService.enqueueForOrder(saved, PrintJobKind.KOT, "auto");
            } catch (Exception ex) {
                log.warn("Failed to enqueue KOT print job for new QR order: {}", ex.getMessage());
            }
        }

        linkCustomerToOrder(saved);

        if (table != null) {
            try {
                if (!"OCCUPIED".equals(table.getStatus())) {
                    table.setStatus("OCCUPIED");
                    tableRepository.save(table);
                }
            } catch (Exception e) {
                log.warn("Could not update table status to OCCUPIED: {}", e.getMessage());
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("orderId", saved.getId());
        response.put("orderNo", saved.getOrderNo());
        response.put("status", saved.getOrderStatus());
        response.put("paymentStatus", saved.getPaymentStatus());
        response.put("grandTotal", saved.getGrandTotal());
        response.put("tableNumber", saved.getTableNumber());
        response.put("isAppended", isAppended);

        return response;
    }

    private RestaurantTable findTable(UUID clientId, UUID orgId, String tableIdentifier) {
        if (tableIdentifier == null || tableIdentifier.isBlank()) return null;
        try {
            UUID tid = UUID.fromString(tableIdentifier);
            return tableRepository.findById(tid).orElse(null);
        } catch (IllegalArgumentException ex) {
            List<RestaurantTable> tables;
            if (orgId != null) {
                tables = tableRepository.findByClientIdAndOrgIdOrderByDisplayOrderAscTableNumberAsc(clientId, orgId);
            } else {
                tables = tableRepository.findByClientIdOrderByDisplayOrderAscTableNumberAsc(clientId);
            }
            return tables.stream()
                    .filter(t -> tableIdentifier.equalsIgnoreCase(t.getTableNumber()) || tableIdentifier.equalsIgnoreCase(t.getName()))
                    .findFirst()
                    .orElse(null);
        }
    }

    private String buildPaymentReference(String paymentMethod, String razorpayPaymentId, String razorpayOrderId) {
        String method = (paymentMethod == null || paymentMethod.isBlank()) ? "CASH" : paymentMethod.toUpperCase();
        if ("RAZORPAY".equals(method)) {
            if (razorpayPaymentId != null && !razorpayPaymentId.isBlank()) {
                return "RAZORPAY:" + razorpayPaymentId;
            }
            if (razorpayOrderId != null && !razorpayOrderId.isBlank()) {
                return "RAZORPAY:" + razorpayOrderId;
            }
        }
        return method;
    }

    private void linkCustomerToOrder(Order order) {
        if (order.getCustomerId() == null || order.getClientId() == null || order.getId() == null) {
            return;
        }
        customerRepository.findByIdAndClientId(order.getCustomerId(), order.getClientId()).ifPresent(customer -> {
            if (customer.getOrderLinks() == null) {
                customer.setOrderLinks(new ArrayList<>());
            }
            customer.getOrderLinks().removeIf(link -> order.getId().equals(link.getOrderId()));
            customer.getOrderLinks().add(Customer.OrderLink.builder()
                    .orderId(order.getId())
                    .isPrimary(true)
                    .attachedAt(Instant.now().toString())
                    .build());
            customerRepository.save(customer);
        });
    }

    private void validateSubscription(UUID clientId, UUID orgId) {
        if (clientId != null) {
            Client client = clientRepository.findById(clientId).orElse(null);
            if (client == null || !client.isSubscriptionActive()) {
                throw new BusinessException("The restaurant's subscription has expired. Online menu and ordering are currently unavailable.");
            }

            boolean tableQrActive = false;
            String status = client.getSubscriptionStatus();
            if (status != null && "TRIAL".equalsIgnoreCase(status.trim()) && client.getSubscriptionExpiryDate() != null && client.getSubscriptionExpiryDate().isAfter(java.time.LocalDateTime.now())) {
                tableQrActive = true;
            } else {
                tableQrActive = systemConfigurationService.isModuleActive(clientId, orgId, ModuleName.TABLE_QR);
            }

            if (!tableQrActive) {
                throw new BusinessException("Online Ordering is not active for this branch. Please contact restaurant administration.");
            }
        }
    }
}
