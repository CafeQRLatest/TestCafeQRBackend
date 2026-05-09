package com.restaurant.pos.order.service;

import com.restaurant.pos.common.exception.ResourceNotFoundException;
import com.restaurant.pos.common.tenant.TenantContext;
import com.restaurant.pos.common.util.SecurityUtils;
import com.restaurant.pos.inventory.service.InventoryService;
import com.restaurant.pos.invoice.domain.Invoice;
import com.restaurant.pos.invoice.domain.InvoiceType;
import com.restaurant.pos.order.domain.Order;
import com.restaurant.pos.order.domain.OrderLine;
import com.restaurant.pos.order.domain.OrderType;
import com.restaurant.pos.order.domain.Payment;
import com.restaurant.pos.order.domain.PaymentType;
import com.restaurant.pos.order.dto.OrderCancelRequest;
import com.restaurant.pos.order.dto.OrderMoveTableRequest;
import com.restaurant.pos.order.dto.OrderSettleRequest;
import com.restaurant.pos.invoice.repository.InvoiceRepository;
import com.restaurant.pos.order.repository.OrderRepository;
import com.restaurant.pos.order.repository.PaymentRepository;
import com.restaurant.pos.print.domain.PrintJobKind;
import com.restaurant.pos.print.service.PrintJobService;
import com.restaurant.pos.product.domain.Product;
import com.restaurant.pos.product.repository.ProductRepository;
import com.restaurant.pos.sequence.domain.DocumentType;
import com.restaurant.pos.sequence.service.DocumentSequenceService;
import com.restaurant.pos.sequence.service.OfflineSequenceLeaseService;
import com.restaurant.pos.table.domain.RestaurantTable;
import com.restaurant.pos.table.repository.RestaurantTableRepository;
import com.restaurant.pos.purchasing.domain.Customer;
import com.restaurant.pos.purchasing.repository.CustomerRepository;
import com.restaurant.pos.table.repository.RestaurantTableRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final InventoryService inventoryService;
    private final RestaurantTableRepository tableRepository;
    private final DocumentSequenceService sequenceService;
    private final OfflineSequenceLeaseService offlineSequenceLeaseService;
    private final PrintJobService printJobService;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;

    private void prepareSourceFields(Order order) {
        UUID terminalId = TenantContext.getCurrentTerminal();
        if (order.getTerminalId() == null) {
            order.setTerminalId(terminalId);
        }
        if (order.getSourceTerminalId() == null) {
            order.setSourceTerminalId(order.getTerminalId() != null ? order.getTerminalId() : terminalId);
        }
        if (order.getSyncOrigin() == null || order.getSyncOrigin().isBlank()) {
            order.setSyncOrigin(order.getSourceOperationId() == null ? "CLOUD_ONLINE" : "OFFLINE_QUEUE");
        }
    }

    private void assignOrderNumber(Order order) {
        OrderType type = order.getOrderType() == null ? OrderType.SALE : order.getOrderType();
        DocumentType docType = switch (type) {
            case PURCHASE -> DocumentType.PURCHASE_ORDER;
            case EXPENSE -> DocumentType.EXPENSE;
            default -> DocumentType.SALE_ORDER;
        };
        order.setOrderNo(resolveDocumentNumber(order, docType, order.getOrderNo()));
    }

    private String resolveDocumentNumber(Order order, DocumentType documentType, String requestedNumber) {
        if (requestedNumber != null && !requestedNumber.isBlank() && isMainOfflineSync(order)) {
            offlineSequenceLeaseService.consumeLeasedNumber(
                    documentType,
                    requestedNumber,
                    order.getSourceTerminalId() != null ? order.getSourceTerminalId() : order.getTerminalId()
            );
            return requestedNumber;
        }
        return sequenceService.generateNextSequence(documentType);
    }

    private boolean isMainOfflineSync(Order order) {
        return order != null && "MAIN_OFFLINE".equalsIgnoreCase(order.getSyncOrigin());
    }

    private boolean shouldGenerateInvoice(Order order) {
        if (order == null || "CANCELLED".equalsIgnoreCase(order.getOrderStatus()) || "VOID".equalsIgnoreCase(order.getOrderStatus())) {
            return false;
        }
        return "COMPLETED".equalsIgnoreCase(order.getOrderStatus()) || "BILLED".equalsIgnoreCase(order.getOrderStatus());
    }

    private void enqueueCloudPrintJobs(Order order) {
        try {
            if (order == null || order.getOrderType() != OrderType.SALE || isMainOfflineSync(order)) {
                return;
            }
            if ("KITCHEN".equalsIgnoreCase(order.getOrderStatus()) || "CONFIRMED".equalsIgnoreCase(order.getOrderStatus())) {
                printJobService.enqueueForOrder(order, PrintJobKind.KOT, "auto");
            } else if ("COMPLETED".equalsIgnoreCase(order.getOrderStatus())) {
                printJobService.enqueueForOrder(order, PrintJobKind.BILL, "auto");
            }
        } catch (Exception ex) {
            log.warn("Unable to enqueue cloud print job for order {}", order == null ? null : order.getId(), ex);
        }
    }

    private Order hydrateOrderLines(Order order) {
        if (order == null || order.getLines() == null || order.getLines().isEmpty()) {
            return order;
        }

        boolean needsHydration = order.getLines().stream()
                .anyMatch(line -> line.getProductId() != null && (
                        line.getProductName() == null || line.getProductName().isBlank()
                                || line.getCategoryName() == null || line.getCategoryName().isBlank()
                                || line.getIsPackagedGood() == null
                ));

        if (!needsHydration) {
            return order;
        }

        List<UUID> productIds = order.getLines().stream()
                .map(OrderLine::getProductId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (productIds.isEmpty()) {
            return order;
        }

        Map<UUID, Product> productsById = productRepository.findByIdIn(productIds).stream()
                .collect(Collectors.toMap(Product::getId, product -> product));

        for (OrderLine line : order.getLines()) {
            Product product = productsById.get(line.getProductId());
            if (product == null) {
                continue;
            }

            if (line.getProductName() == null || line.getProductName().isBlank()) {
                line.setProductName(product.getName());
            }
            if (line.getCategoryName() == null || line.getCategoryName().isBlank()) {
                line.setCategoryName(product.getCategory() != null ? product.getCategory().getName() : null);
            }
            if (line.getIsPackagedGood() == null) {
                line.setIsPackagedGood(product.isPackagedGood());
            }
        }

        return order;
    }

    private List<Order> hydrateOrderLines(List<Order> orders) {
        orders.forEach(this::hydrateOrderLines);
        return orders;
    }

    /**
     * Ensures a customer record exists for the order.
     * If customerName/customerPhone are provided but customerId is null,
     * searches for an existing customer by phone (or name). If found, links it.
     * If not found, auto-creates a new customer record.
     */
    private void hydrateCustomer(Order order) {
        if (order == null) return;
        if (order.getOrderType() != null && order.getOrderType() != OrderType.SALE) return;

        UUID clientId = order.getClientId();
        UUID orgId = order.getOrgId();

        String custName = order.getCustomerName() != null ? order.getCustomerName().trim() : null;
        String custPhone = order.getCustomerPhone() != null ? order.getCustomerPhone().trim() : null;

        if ((custName == null || custName.isEmpty()) && (custPhone == null || custPhone.isEmpty())) {
            return; // No customer info provided
        }

        // If customerId is already set, populate name/phone from the record if missing
        if (order.getCustomerId() != null) {
            if ((custName == null || custName.isEmpty()) || (custPhone == null || custPhone.isEmpty())) {
                customerRepository.findByIdAndClientId(order.getCustomerId(), clientId)
                    .ifPresent(c -> {
                        if (order.getCustomerName() == null || order.getCustomerName().isBlank()) {
                            order.setCustomerName(c.getName());
                        }
                        if (order.getCustomerPhone() == null || order.getCustomerPhone().isBlank()) {
                            order.setCustomerPhone(c.getPhone());
                        }
                    });
            }
            return;
        }

        // Try to find existing customer by phone (strong match)
        Optional<Customer> existing = Optional.empty();
        if (custPhone != null && !custPhone.isEmpty()) {
            existing = customerRepository.findByPhoneAndClientId(custPhone, clientId);
        }

        if (existing.isPresent()) {
            Customer c = existing.get();
            order.setCustomerId(c.getId());
            if (order.getCustomerName() == null || order.getCustomerName().isBlank()) {
                order.setCustomerName(c.getName());
            }
            return;
        }

        // Auto-create a new customer
        Customer newCustomer = Customer.builder()
                .name(custName != null && !custName.isEmpty() ? custName : "Guest")
                .phone(custPhone)
                .customerCategory("REGULAR")
                .build();
        newCustomer.setClientId(clientId);
        newCustomer.setOrgId(orgId);

        Customer saved = customerRepository.save(newCustomer);
        order.setCustomerId(saved.getId());
        log.info("Auto-created customer '{}' (phone: {}) for order {}", saved.getName(), saved.getPhone(), order.getOrderNo());
    }

    public List<Order> getOrders(String status) {
        UUID tenantId = TenantContext.getCurrentTenant();
        List<String> statuses = (status != null && !status.isEmpty()) ? Arrays.asList(status.split(",")) : null;

        List<Order> orders;
        if (SecurityUtils.isSuperAdmin()) {
            if (statuses != null) orders = orderRepository.findByClientIdAndOrderStatusInOrderByCreatedAtDesc(tenantId, statuses);
            else orders = orderRepository.findByClientIdOrderByCreatedAtDesc(tenantId);
        } else {
            if (statuses != null) orders = orderRepository.findByClientIdAndOrgIdAndOrderStatusInOrderByCreatedAtDesc(tenantId, TenantContext.getCurrentOrg(), statuses);
            else orders = orderRepository.findByClientIdAndOrgIdOrderByCreatedAtDesc(tenantId, TenantContext.getCurrentOrg());
        }

        // Lazy generate documents for completed orders that are missing them
        orders.stream()
            .filter(o -> "COMPLETED".equalsIgnoreCase(o.getOrderStatus()) && (o.getInvoiceNo() == null || o.getInvoiceNo().isEmpty()))
            .forEach(o -> {
                try { generateInvoice(o); } catch (Exception ignored) {}
            });

        return hydrateOrderLines(orders);
    }
    
    public List<Order> getOrders() {
        return getOrders(null);
    }

    @Transactional(readOnly = true)
    public List<Order> getOrdersByType(OrderType orderType) {
        UUID tenantId = TenantContext.getCurrentTenant();
        if (SecurityUtils.isSuperAdmin()) {
            return hydrateOrderLines(orderRepository.findByClientIdAndOrderTypeOrderByCreatedAtDesc(tenantId, orderType));
        }
        return hydrateOrderLines(orderRepository.findByClientIdAndOrgIdAndOrderTypeOrderByCreatedAtDesc(
                tenantId, TenantContext.getCurrentOrg(), orderType));
    }

    @Transactional(readOnly = true)
    public List<Order> searchOrders(com.restaurant.pos.order.dto.OrderSearchCriteria criteria) {
        UUID clientId = TenantContext.getCurrentTenant();
        UUID orgId = TenantContext.getCurrentOrg();
        
        org.springframework.data.jpa.domain.Specification<Order> spec = 
            com.restaurant.pos.order.spec.OrderSpecification.filterBy(criteria, clientId, orgId);
            
        return hydrateOrderLines(orderRepository.findAll(spec, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt")));
    }

    @Transactional(readOnly = true)
    public Order getOrder(UUID id) {
        UUID tenantId = TenantContext.getCurrentTenant();
        if (SecurityUtils.isSuperAdmin()) {
            return hydrateOrderLines(orderRepository.findByIdAndClientId(id, tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found or access denied")));
        }
        return hydrateOrderLines(orderRepository.findByIdAndClientIdAndOrgId(id, tenantId, TenantContext.getCurrentOrg())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found or access denied")));
    }

    @Transactional
    public Order createOrder(Order order) {
        log.info("Creating order: {} | Tenant: {} | Org: {}", order, TenantContext.getCurrentTenant(), TenantContext.getCurrentOrg());
        String requestedInvoiceNo = order.getOfflineInvoiceNo();
        String requestedPaymentNo = order.getOfflinePaymentNo();
        
        order.setClientId(TenantContext.getCurrentTenant());
        if (!SecurityUtils.isSuperAdmin() || order.getOrgId() == null) {
            order.setOrgId(TenantContext.getCurrentOrg());
        }
        prepareSourceFields(order);

        if (order.getOrderStatus() == null) {
            order.setOrderStatus("DRAFT");
        }

        assignOrderNumber(order);

        // Ensure bidirectional mapping
        if (order.getLines() != null) {
            order.getLines().forEach(line -> line.setOrder(order));
        }
        hydrateOrderLines(order);
        hydrateCustomer(order);

        Order saved = orderRepository.save(order);
        
        if (shouldGenerateInvoice(saved)) {
            generateInvoice(saved, null, requestedInvoiceNo);
        }
        
        // Create payment only if completed and paid
        if ("COMPLETED".equalsIgnoreCase(saved.getOrderStatus()) && "PAID".equalsIgnoreCase(saved.getPaymentStatus())) {
            String paymentMethod = saved.getReference() != null ? saved.getReference() : "CASH";
            generatePayment(saved, paymentMethod, requestedPaymentNo);
        }

        handleTableStatus(saved);
        Order hydrated = hydrateOrderLines(saved);
        enqueueCloudPrintJobs(hydrated);
        return hydrated;
    }

    @Transactional(readOnly = true)
    public Optional<Order> findBySourceOperationId(String sourceOperationId) {
        if (sourceOperationId == null || sourceOperationId.isBlank()) {
            return Optional.empty();
        }
        return orderRepository.findBySourceOperationIdAndClientId(sourceOperationId, TenantContext.getCurrentTenant())
                .map(this::hydrateOrderLines);
    }

    @Transactional
    public Order updateOrder(UUID id, Order updates) {
        Order oldOrder = getOrder(id);
        
        // 1. Create a deep copy of the old order as a VOID record
        String originalOrderNo = oldOrder.getOrderNo();
        oldOrder.setOrderNo(originalOrderNo + "_VOID_" + (oldOrder.getRevisionNumber() != null ? oldOrder.getRevisionNumber() : 0));
        oldOrder.setOrderStatus("VOID");
        oldOrder.setIsactive("N");
        orderRepository.save(oldOrder);
        
        // 2. VOID the linked invoice
        List<UUID> oldInvoiceIdList = new java.util.ArrayList<>();
        invoiceRepository.findByOrderId(id).forEach(inv -> {
            oldInvoiceIdList.add(inv.getId());
            inv.setInvoiceNo(inv.getInvoiceNo() + "_VOID_" + (oldOrder.getRevisionNumber() != null ? oldOrder.getRevisionNumber() : 0));
            inv.setStatus("VOID");
            invoiceRepository.save(inv);
        });

        // 3. Create NEW order record with the original Order No
        Order newOrder = Order.builder()
            .terminalId(oldOrder.getTerminalId())
            .orderNo(originalOrderNo)
            .orderType(oldOrder.getOrderType())
            .orderStatus(updates.getOrderStatus() != null ? updates.getOrderStatus() : oldOrder.getOrderStatus())
            .paymentStatus(updates.getPaymentStatus() != null ? updates.getPaymentStatus() : oldOrder.getPaymentStatus())
            .orderSource(oldOrder.getOrderSource())
            .sourceDeviceId(oldOrder.getSourceDeviceId())
            .sourceTerminalId(oldOrder.getSourceTerminalId())
            .sourceOperationId(oldOrder.getSourceOperationId())
            .sourceOfflineId(oldOrder.getSourceOfflineId())
            .sourceLocalRef(oldOrder.getSourceLocalRef())
            .offlineCreatedAt(oldOrder.getOfflineCreatedAt())
            .syncOrigin(oldOrder.getSyncOrigin())
            .fulfillmentType(updates.getFulfillmentType())
            .tableNumber(updates.getTableNumber())
            .tableId(updates.getTableId())
            .customerId(updates.getCustomerId())
            .customerName(updates.getCustomerName())
            .customerPhone(updates.getCustomerPhone())
            .customerIds(updates.getCustomerIds())
            .totalAmount(updates.getTotalAmount())
            .totalTaxAmount(updates.getTotalTaxAmount())
            .totalDiscountAmount(updates.getTotalDiscountAmount())
            .grandTotal(updates.getGrandTotal())
            .description(updates.getDescription())
            .originalOrderId(oldOrder.getId())
            .revisionNumber((oldOrder.getRevisionNumber() != null ? oldOrder.getRevisionNumber() : 0) + 1)
            .build();
            
        // Manually set inherited fields
        newOrder.setClientId(oldOrder.getClientId());
        newOrder.setOrgId(oldOrder.getOrgId());

        if (updates.getLines() != null) {
            updates.getLines().forEach(newOrder::addLine);
        }
        hydrateOrderLines(newOrder);
        
        Order saved = orderRepository.save(newOrder);
        
        // 4. Generate new ERP documents (Invoice/Payment)
        UUID oldInvId = oldInvoiceIdList.isEmpty() ? null : oldInvoiceIdList.get(0);
        if (shouldGenerateInvoice(saved)) {
            generateInvoice(saved, oldInvId);
        }
        if ("PAID".equalsIgnoreCase(saved.getPaymentStatus())) {
            String paymentMethod = saved.getReference() != null ? saved.getReference() : "CASH";
            generatePayment(saved, paymentMethod);
        }
        
        handleTableStatus(saved);

        // Inventory Hook: If PURCHASE order is COMPLETED, update stock
        if (saved.getOrderType() == OrderType.PURCHASE && "COMPLETED".equalsIgnoreCase(saved.getOrderStatus())) {
            processInventoryForOrder(saved);
        }
        
        Order hydrated = hydrateOrderLines(saved);
        enqueueCloudPrintJobs(hydrated);
        return hydrated;
    }

    @Transactional
    public Order updateOrderStatus(UUID id, String status, String paymentStatus, String description) {
        Order order = getOrder(id);
        if (status != null) order.setOrderStatus(status);
        if (paymentStatus != null) order.setPaymentStatus(paymentStatus);
        if (description != null) order.setDescription(description);
        
        Order result = orderRepository.save(order);
        
        if (shouldGenerateInvoice(result)) {
            generateInvoice(result);
        }

        // Generate Payment only if completed and paid
        if ("COMPLETED".equalsIgnoreCase(result.getOrderStatus()) && "PAID".equalsIgnoreCase(result.getPaymentStatus())) {
            String paymentMethod = result.getReference() != null ? result.getReference() : "CASH";
            generatePayment(result, paymentMethod);
        }
        
        handleTableStatus(result);

        if (result.getOrderType() == OrderType.PURCHASE && "COMPLETED".equalsIgnoreCase(result.getOrderStatus())) {
            processInventoryForOrder(result);
        }
        Order hydrated = hydrateOrderLines(result);
        enqueueCloudPrintJobs(hydrated);
        return hydrated;
    }

    @Transactional
    public Order updateOrderStatus(UUID id, String status) {
        return updateOrderStatus(id, status, null, null);
    }

    @Transactional
    public Order billOrder(UUID id) {
        Order order = getOrder(id);
        ensureOrderCanChange(order, "bill");

        order.setOrderStatus("BILLED");
        if (!"PAID".equalsIgnoreCase(order.getPaymentStatus())) {
            order.setPaymentStatus("PENDING");
        }

        Order saved = orderRepository.save(order);
        generateInvoice(saved);
        handleTableStatus(saved);
        return hydrateOrderLines(saved);
    }

    @Transactional
    public Order settleOrder(UUID id, OrderSettleRequest request) {
        Order order = getOrder(id);
        ensureOrderCanChange(order, "settle");

        OrderSettleRequest safeRequest = request == null ? new OrderSettleRequest() : request;
        String paymentMethod = normalizePaymentMethod(safeRequest.getPaymentMethod());
        BigDecimal discountAmount = moneyValue(safeRequest.getDiscountAmount());
        BigDecimal roundOffAmount = moneyValue(safeRequest.getRoundOffAmount());
        BigDecimal currentTotal = moneyValue(order.getGrandTotal());

        if (discountAmount.compareTo(BigDecimal.ZERO) > 0) {
            order.setTotalDiscountAmount(moneyValue(order.getTotalDiscountAmount()).add(discountAmount));
        }

        BigDecimal payable = currentTotal.subtract(discountAmount).add(roundOffAmount);
        if (payable.compareTo(BigDecimal.ZERO) < 0) {
            payable = BigDecimal.ZERO;
        }
        payable = payable.setScale(2, RoundingMode.HALF_UP);

        if (discountAmount.compareTo(BigDecimal.ZERO) > 0 || roundOffAmount.compareTo(BigDecimal.ZERO) != 0) {
            order.setGrandTotal(payable);
        }

        order.setReference(paymentMethod);
        order.setOrderStatus("COMPLETED");
        order.setPaymentStatus("PAID");

        String settlementDescription = buildSettlementDescription(safeRequest, paymentMethod);
        if (!settlementDescription.isBlank()) {
            order.setDescription(appendDescription(order.getDescription(), settlementDescription));
        }

        Order saved = orderRepository.save(order);
        generateInvoice(saved);

        BigDecimal amountPaid = safeRequest.getAmountPaid() != null
                ? moneyValue(safeRequest.getAmountPaid())
                : payable;
        generatePayment(saved, paymentMethod, null, amountPaid, settlementDescription);

        handleTableStatus(saved);

        if (saved.getOrderType() == OrderType.PURCHASE && "COMPLETED".equalsIgnoreCase(saved.getOrderStatus())) {
            processInventoryForOrder(saved);
        }

        return hydrateOrderLines(saved);
    }

    @Transactional
    public Order moveTable(UUID id, OrderMoveTableRequest request) {
        if (request == null || request.getTableId() == null) {
            throw new IllegalArgumentException("Target table is required");
        }

        Order order = getOrder(id);
        ensureOrderCanChange(order, "move");

        UUID oldTableId = order.getTableId();
        RestaurantTable targetTable = getTenantTable(request.getTableId());

        order.setTableId(targetTable.getId());
        order.setTableNumber(targetTable.getTableNumber());
        order.setFulfillmentType("DINE_IN");

        Order saved = orderRepository.save(order);
        if (oldTableId != null && !oldTableId.equals(targetTable.getId())) {
            setTableStatus(oldTableId, "AVAILABLE");
        }
        handleTableStatus(saved);
        return hydrateOrderLines(saved);
    }

    @Transactional
    public Order cancelOrder(UUID id, OrderCancelRequest request) {
        Order order = getOrder(id);
        ensureOrderCanChange(order, "cancel");

        String reason = request != null ? request.getReason() : null;
        order.setOrderStatus("CANCELLED");
        if (reason != null && !reason.isBlank()) {
            order.setDescription(appendDescription(order.getDescription(), "Cancel reason: " + reason.trim()));
        }

        Order saved = orderRepository.save(order);
        invoiceRepository.findByOrderId(saved.getId()).forEach(invoice -> {
            if (!Boolean.TRUE.equals(invoice.getIsPaid())) {
                invoice.setStatus("VOID");
                invoice.setDocStatus("VOID");
                invoice.setAmountDue(BigDecimal.ZERO);
                invoiceRepository.save(invoice);
            }
        });

        handleTableStatus(saved);
        return hydrateOrderLines(saved);
    }

    @Transactional
    public Invoice generateInvoice(Order order) {
        return generateInvoice(order, null, null);
    }

    @Transactional
    public Invoice generateInvoice(Order order, UUID originalInvoiceId) {
        return generateInvoice(order, originalInvoiceId, null);
    }

    @Transactional
    public Invoice generateInvoice(Order order, UUID originalInvoiceId, String requestedInvoiceNo) {
        if (!invoiceRepository.findByOrderId(order.getId()).isEmpty()) return null;

        UUID clientId = order.getClientId();
        UUID orgId = order.getOrgId();
        
        // Determine invoice document type from order type
        OrderType orderType = order.getOrderType() == null ? OrderType.SALE : order.getOrderType();
        DocumentType invoiceDocType = switch (orderType) {
            case PURCHASE -> DocumentType.VENDOR_BILL;
            case EXPENSE  -> DocumentType.EXPENSE_RECEIPT;
            default       -> DocumentType.CUSTOMER_INVOICE;
        };
        
        String invNo = resolveDocumentNumber(order, invoiceDocType, requestedInvoiceNo);
        
        // Map to entity InvoiceType
        InvoiceType invoiceType = switch (invoiceDocType) {
            case VENDOR_BILL -> InvoiceType.VENDOR_BILL;
            case EXPENSE_RECEIPT -> InvoiceType.EXPENSE_RECEIPT;
            default -> InvoiceType.CUSTOMER_INVOICE;
        };

        Invoice invoice = Invoice.builder()
            .invoiceType(invoiceType)
            .terminalId(order.getTerminalId())
            .sourceDeviceId(order.getSourceDeviceId())
            .sourceTerminalId(order.getSourceTerminalId())
            .sourceOperationId(order.getSourceOperationId())
            .sourceOfflineId(order.getSourceOfflineId())
            .sourceLocalRef(order.getSourceLocalRef())
            .offlineCreatedAt(order.getOfflineCreatedAt())
            .syncOrigin(order.getSyncOrigin())
            .orderId(order.getId())
            .customerId(order.getCustomerId())
            .vendorId(order.getVendorId())
            .invoiceNo(invNo)
            .totalAmount(order.getGrandTotal())
            .amountDue(order.getGrandTotal())
            .status("UNPAID")
            .isPaid(false)
            .isCredit("PENDING".equalsIgnoreCase(order.getPaymentStatus()))
            .originalInvoiceId(originalInvoiceId)
            .build();
            
        invoice.setClientId(clientId);
        invoice.setOrgId(orgId);
            
        return invoiceRepository.save(invoice);
    }

    @Transactional
    public void generatePayment(Order order) {
        generatePayment(order, "CASH");
    }

    @Transactional
    public void generatePayment(Order order, String paymentMethod) {
        generatePayment(order, paymentMethod, null);
    }

    @Transactional
    public void generatePayment(Order order, String paymentMethod, String requestedPaymentNo) {
        generatePayment(order, paymentMethod, requestedPaymentNo, order.getGrandTotal(), null);
    }

    @Transactional
    public void generatePayment(Order order, String paymentMethod, String requestedPaymentNo, BigDecimal amountPaid, String description) {
        if (order.getPaymentNo() != null && !order.getPaymentNo().isEmpty()) return;

        // Try to find the invoice
        List<Invoice> invoices = invoiceRepository.findByOrderId(order.getId());
        Invoice invoice = invoices.isEmpty() ? generateInvoice(order) : invoices.get(0);

        UUID clientId = order.getClientId();
        UUID orgId = order.getOrgId();
        
        // INBOUND = money received (Sales), OUTBOUND = money paid (Purchase/Expense)
        DocumentType paymentDocType = (order.getOrderType() == null || order.getOrderType() == OrderType.SALE)
                ? DocumentType.INBOUND_PAYMENT
                : DocumentType.OUTBOUND_PAYMENT;
                
        String payNo = resolveDocumentNumber(order, paymentDocType, requestedPaymentNo);

        PaymentType paymentType = (paymentDocType == DocumentType.INBOUND_PAYMENT)
                ? PaymentType.INBOUND
                : PaymentType.OUTBOUND;
        
        Payment payment = Payment.builder()
            .paymentType(paymentType)
            .terminalId(order.getTerminalId())
            .sourceDeviceId(order.getSourceDeviceId())
            .sourceTerminalId(order.getSourceTerminalId())
            .sourceOperationId(order.getSourceOperationId())
            .sourceOfflineId(order.getSourceOfflineId())
            .sourceLocalRef(order.getSourceLocalRef())
            .offlineCreatedAt(order.getOfflineCreatedAt())
            .syncOrigin(order.getSyncOrigin())
            .orderId(order.getId())
            .invoiceId(invoice != null ? invoice.getId() : null)
            .paymentMethod(paymentMethod)
            .amountPaid(moneyValue(amountPaid))
            .referenceNo(payNo)
            .description(description)
            .status("COMPLETED")
            .build();
            
        payment.setClientId(clientId);
        payment.setOrgId(orgId);
            
        paymentRepository.save(payment);
        
        // Update Invoice status if it exists
        if (invoice != null) {
            invoice.setStatus("PAID");
            invoice.setIsPaid(true);
            invoice.setAmountDue(BigDecimal.ZERO);
            invoiceRepository.save(invoice);
        }
    }

    private void handleTableStatus(Order order) {
        if (order.getTableId() == null) return;

        boolean shouldRelease = "COMPLETED".equalsIgnoreCase(order.getOrderStatus()) ||
                                "CANCELLED".equalsIgnoreCase(order.getOrderStatus()) ||
                                "PAID".equalsIgnoreCase(order.getPaymentStatus());

        String nextStatus = shouldRelease
                ? "AVAILABLE"
                : "BILLED".equalsIgnoreCase(order.getOrderStatus()) ? "BILLED" : "OCCUPIED";

        tableRepository.findById(order.getTableId()).ifPresent(table -> {
            if (!nextStatus.equalsIgnoreCase(String.valueOf(table.getStatus()))) {
                table.setStatus(nextStatus);
                tableRepository.save(table);
            }
        });
    }

    private RestaurantTable getTenantTable(UUID tableId) {
        UUID tenantId = TenantContext.getCurrentTenant();
        if (SecurityUtils.isSuperAdmin()) {
            return tableRepository.findByIdAndClientId(tableId, tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Target table not found"));
        }
        return tableRepository.findByIdAndClientIdAndOrgId(tableId, tenantId, TenantContext.getCurrentOrg())
                .orElseThrow(() -> new ResourceNotFoundException("Target table not found"));
    }

    private void setTableStatus(UUID tableId, String status) {
        tableRepository.findById(tableId).ifPresent(table -> {
            table.setStatus(status);
            tableRepository.save(table);
        });
    }

    private void ensureOrderCanChange(Order order, String action) {
        if (order == null) {
            throw new ResourceNotFoundException("Order not found");
        }
        String status = String.valueOf(order.getOrderStatus());
        if ("CANCELLED".equalsIgnoreCase(status) || "VOID".equalsIgnoreCase(status)) {
            throw new IllegalStateException("Cannot " + action + " a " + status.toLowerCase() + " order");
        }
        if (!"settle".equalsIgnoreCase(action)
                && ("COMPLETED".equalsIgnoreCase(status) || "PAID".equalsIgnoreCase(order.getPaymentStatus()))) {
            throw new IllegalStateException("Cannot " + action + " a completed order");
        }
    }

    private String normalizePaymentMethod(String paymentMethod) {
        String method = paymentMethod == null || paymentMethod.isBlank() ? "CASH" : paymentMethod.trim().toUpperCase();
        if (!List.of("CASH", "ONLINE", "MIXED").contains(method)) {
            return "CASH";
        }
        return method;
    }

    private BigDecimal moneyValue(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private String buildSettlementDescription(OrderSettleRequest request, String paymentMethod) {
        List<String> parts = new java.util.ArrayList<>();
        if ("MIXED".equalsIgnoreCase(paymentMethod)) {
            parts.add("Cash: " + moneyValue(request.getCashAmount()));
            parts.add("Online: " + moneyValue(request.getOnlineAmount()));
        }
        if (request.getDiscountAmount() != null && moneyValue(request.getDiscountAmount()).compareTo(BigDecimal.ZERO) > 0) {
            parts.add("Discount: " + moneyValue(request.getDiscountAmount()));
        }
        if (request.getRoundOffAmount() != null && moneyValue(request.getRoundOffAmount()).compareTo(BigDecimal.ZERO) != 0) {
            parts.add("Round off: " + moneyValue(request.getRoundOffAmount()));
        }
        if (request.getDescription() != null && !request.getDescription().isBlank()) {
            parts.add(request.getDescription().trim());
        }
        return String.join("; ", parts);
    }

    private String appendDescription(String existing, String addition) {
        if (addition == null || addition.isBlank()) {
            return existing;
        }
        if (existing == null || existing.isBlank()) {
            return addition;
        }
        return existing + "\n" + addition;
    }

    private void processInventoryForOrder(Order order) {
        if (order.getWarehouseId() == null) {
            // Log warning or throw exception? For now, we need a warehouse to receive stock.
            return;
        }

        if (order.getLines() != null) {
            for (com.restaurant.pos.order.domain.OrderLine line : order.getLines()) {
                inventoryService.updateStock(
                    order.getWarehouseId(),
                    line.getProductId(),
                    line.getVariantId(),
                    line.getQuantity(),
                    "PURCHASE",
                    order.getId(),
                    line.getUnitPrice()
                );
            }
        }
    }
}
