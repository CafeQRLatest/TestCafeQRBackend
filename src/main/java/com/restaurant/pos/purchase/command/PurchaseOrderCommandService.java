package com.restaurant.pos.purchase.command;

import com.restaurant.pos.common.exception.BusinessException;
import com.restaurant.pos.common.exception.ResourceNotFoundException;
import com.restaurant.pos.common.service.BranchContextService;
import com.restaurant.pos.common.tenant.TenantContext;
import com.restaurant.pos.order.domain.Order;
import com.restaurant.pos.order.domain.OrderLine;
import com.restaurant.pos.order.domain.OrderStatus;
import com.restaurant.pos.order.domain.OrderType;
import com.restaurant.pos.order.dto.OrderResponseDto;
import com.restaurant.pos.order.repository.OrderRepository;
import com.restaurant.pos.purchase.domain.PurchaseOrder;
import com.restaurant.pos.purchase.dto.BulkPurchaseOrderResponseDto;
import com.restaurant.pos.purchase.event.PurchaseOrderCompletedEvent;
import com.restaurant.pos.purchase.event.PurchaseOrderVoidedEvent;
import com.restaurant.pos.purchase.mapper.PurchaseOrderDtoMapper;
import com.restaurant.pos.sequence.domain.DocumentType;
import com.restaurant.pos.sequence.service.DocumentSequenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * CQRS Command Service for Purchase Orders.
 * Enforces server-side financial total recalculation, domain entity state transitions,
 * and publishes domain events (PurchaseOrderCompletedEvent, PurchaseOrderVoidedEvent) for decoupled listeners.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseOrderCommandService {

    private final OrderRepository orderRepository;
    private final DocumentSequenceService documentSequenceService;
    private final PurchaseOrderDtoMapper purchaseOrderDtoMapper;
    private final BranchContextService branchContext;
    private final ApplicationEventPublisher eventPublisher;
    private final com.restaurant.pos.inventory.service.InventoryService inventoryService;

    /**
     * Creates and saves a new Purchase Order with server-side total recalculation.
     */
    @Transactional
    public OrderResponseDto createPurchaseOrder(CreatePurchaseOrderCommand command, String idempotencyKey) {
        validateCreateCommand(command);

        UUID clientId = TenantContext.getCurrentTenant();
        UUID orgId = branchContext.requireWriteOrgId(command.getOrgId());
        String sourceRef = StringUtils.hasText(idempotencyKey) ? idempotencyKey : command.getSourceLocalRef();

        // Idempotency check: Return existing order if identical sourceLocalRef was already processed
        if (StringUtils.hasText(sourceRef)) {
            var existingOpt = orderRepository.findByClientIdAndOrgIdAndSourceLocalRefAndOrderStatusNot(
                    clientId, orgId, sourceRef, "VOID");
            if (existingOpt.isPresent()) {
                log.info("Idempotent hit: returning existing Purchase Order {} for sourceRef {}", 
                        existingOpt.get().getOrderNo(), sourceRef);
                return purchaseOrderDtoMapper.toResponseDto(existingOpt.get());
            }
        }

        String orderNo = documentSequenceService.generateNextSequence(DocumentType.PURCHASE_ORDER, orgId);

        Order mappedOrder = purchaseOrderDtoMapper.toEntity(command);
        PurchaseOrder purchaseOrder;
        if (mappedOrder instanceof PurchaseOrder po) {
            purchaseOrder = po;
        } else {
            purchaseOrder = new PurchaseOrder();
            purchaseOrder.setVendorId(mappedOrder.getVendorId());
            purchaseOrder.setWarehouseId(mappedOrder.getWarehouseId());
            purchaseOrder.setLines(mappedOrder.getLines());
        }

        purchaseOrder.setClientId(clientId);
        purchaseOrder.setOrgId(orgId);
        purchaseOrder.setOrderNo(orderNo);
        purchaseOrder.setOrderType(OrderType.PURCHASE);
        purchaseOrder.setSourceLocalRef(sourceRef);

        if (!StringUtils.hasText(purchaseOrder.getOrderStatus())) {
            purchaseOrder.setOrderStatus(OrderStatus.DRAFT.name());
        }
        if (command.getIsReceived() != null) {
            purchaseOrder.setIsReceived(command.getIsReceived());
        } else {
            purchaseOrder.setIsReceived(OrderStatus.COMPLETED.name().equalsIgnoreCase(purchaseOrder.getOrderStatus()));
        }
        if (!StringUtils.hasText(purchaseOrder.getPaymentStatus())) {
            purchaseOrder.setPaymentStatus("PENDING");
        }

        if (purchaseOrder.getWarehouseId() == null && orgId != null) {
            inventoryService.findDefaultWarehouse(clientId, orgId)
                    .ifPresent(dw -> purchaseOrder.setWarehouseId(dw.getId()));
        }

        // Link child lines to parent order
        if (purchaseOrder.getLines() != null) {
            for (OrderLine line : purchaseOrder.getLines()) {
                line.setOrder(purchaseOrder);
            }
        }

        // SERVER-SIDE RECALCULATION: Never trust client totals!
        recalculateTotals(purchaseOrder);

        log.info("Creating Purchase Order | orderNo={} | vendorId={} | warehouseId={} | grandTotal={} | lineCount={}",
                orderNo, command.getVendorId(), command.getWarehouseId(),
                purchaseOrder.getGrandTotal(), purchaseOrder.getLines() != null ? purchaseOrder.getLines().size() : 0);

        Order savedOrder = orderRepository.save(purchaseOrder);

        // Fire domain event for completed/confirmed orders (vendor bill generation, accounting, etc.)
        if (OrderStatus.COMPLETED.name().equalsIgnoreCase(savedOrder.getOrderStatus()) || OrderStatus.CONFIRMED.name().equalsIgnoreCase(savedOrder.getOrderStatus())) {
            eventPublisher.publishEvent(new PurchaseOrderCompletedEvent(savedOrder));
        }

        return purchaseOrderDtoMapper.toResponseDto(savedOrder);
    }

    /**
     * Updates an existing Purchase Order using domain methods.
     * If the order was previously completed, publishes a void event to reverse stock and void prior invoices/payments.
     */
    @Transactional
    public OrderResponseDto updatePurchaseOrder(UUID orderId, UpdatePurchaseOrderCommand command) {
        UUID clientId = TenantContext.getCurrentTenant();

        Order existingEntity = orderRepository.findByIdAndClientId(orderId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order not found: " + orderId));

        if (existingEntity.getOrderType() != null && existingEntity.getOrderType() != OrderType.PURCHASE) {
            throw new BusinessException("Order " + orderId + " is not a Purchase Order");
        }

        boolean wasCompleted = OrderStatus.COMPLETED.name().equalsIgnoreCase(existingEntity.getOrderStatus());

        // If the order was previously COMPLETED, notify listeners to void prior invoice/payment and reverse old stock
        if (wasCompleted) {
            eventPublisher.publishEvent(new PurchaseOrderVoidedEvent(existingEntity));
        }

        // Update fields if provided
        if (command.getVendorId() != null) existingEntity.setVendorId(command.getVendorId());
        if (command.getWarehouseId() != null) existingEntity.setWarehouseId(command.getWarehouseId());
        if (command.getPaymentStatus() != null) existingEntity.setPaymentStatus(command.getPaymentStatus());
        if (command.getPaymentMethod() != null) existingEntity.setPaymentMethod(command.getPaymentMethod());
        if (command.getReference() != null) existingEntity.setReference(command.getReference());
        if (command.getDescription() != null) existingEntity.setDescription(command.getDescription());
        if (command.getIsReceived() != null) existingEntity.setIsReceived(command.getIsReceived());

        // Replace lines if provided
        if (command.getLines() != null) {
            Order updatedLinesEntity = purchaseOrderDtoMapper.toEntity(command);
            existingEntity.getLines().clear();
            if (updatedLinesEntity.getLines() != null) {
                for (OrderLine line : updatedLinesEntity.getLines()) {
                    line.setOrder(existingEntity);
                    existingEntity.getLines().add(line);
                }
            }
        }

        // SERVER-SIDE RECALCULATION: Never trust client totals!
        recalculateTotals(existingEntity);

        // State transition via domain methods if status is changing
        if (StringUtils.hasText(command.getOrderStatus())) {
            String targetStatus = command.getOrderStatus().toUpperCase();
            if (OrderStatus.COMPLETED.name().equals(targetStatus) && existingEntity instanceof PurchaseOrder po) {
                po.complete();
                existingEntity.setIsReceived(true);
            } else if (OrderStatus.CONFIRMED.name().equals(targetStatus) && existingEntity instanceof PurchaseOrder po) {
                po.confirm();
            } else if ((OrderStatus.VOID.name().equals(targetStatus) || OrderStatus.CANCELLED.name().equals(targetStatus)) && existingEntity instanceof PurchaseOrder po) {
                po.voidOrder();
            } else {
                existingEntity.setOrderStatus(targetStatus);
            }
        }

        log.info("Updating Purchase Order | id={} | orderNo={} | status={} | grandTotal={}",
                orderId, existingEntity.getOrderNo(), existingEntity.getOrderStatus(), existingEntity.getGrandTotal());

        Order savedOrder = orderRepository.save(existingEntity);

        // Fire domain event when transitioning to or remaining in COMPLETED
        boolean isNowCompleted = OrderStatus.COMPLETED.name().equalsIgnoreCase(savedOrder.getOrderStatus());
        if (isNowCompleted) {
            eventPublisher.publishEvent(new PurchaseOrderCompletedEvent(savedOrder));
        }

        return purchaseOrderDtoMapper.toResponseDto(savedOrder);
    }

    /**
     * Voids / cancels an existing Purchase Order and publishes PurchaseOrderVoidedEvent.
     */
    @Transactional
    public OrderResponseDto voidPurchaseOrder(UUID orderId) {
        UUID clientId = TenantContext.getCurrentTenant();

        Order existingEntity = orderRepository.findByIdAndClientId(orderId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order not found: " + orderId));

        if (existingEntity.getOrderType() != null && existingEntity.getOrderType() != OrderType.PURCHASE) {
            throw new BusinessException("Order " + orderId + " is not a Purchase Order");
        }

        if (existingEntity instanceof PurchaseOrder po) {
            po.voidOrder();
        } else {
            if ("VOID".equalsIgnoreCase(existingEntity.getOrderStatus()) || "CANCELLED".equalsIgnoreCase(existingEntity.getOrderStatus())) {
                throw new BusinessException("Purchase Order is already voided");
            }
            existingEntity.setOrderStatus(OrderStatus.VOID.name());
        }

        log.info("Voiding Purchase Order | id={} | orderNo={}", orderId, existingEntity.getOrderNo());
        Order savedOrder = orderRepository.save(existingEntity);

        // Publish decoupled domain event to void linked invoices, payments, and reverse stock
        eventPublisher.publishEvent(new PurchaseOrderVoidedEvent(savedOrder));

        return purchaseOrderDtoMapper.toResponseDto(savedOrder);
    }

    /**
     * Receives an existing Purchase Order (marks COMPLETED, triggers stock intake & vendor bill).
     */
    @Transactional
    public OrderResponseDto receivePurchaseOrder(UUID orderId) {
        UUID clientId = TenantContext.getCurrentTenant();

        Order existingEntity = orderRepository.findByIdAndClientId(orderId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order not found: " + orderId));

        if (existingEntity.getOrderType() != null && existingEntity.getOrderType() != OrderType.PURCHASE) {
            throw new BusinessException("Order " + orderId + " is not a Purchase Order");
        }

        boolean isAlreadyReceived = Boolean.TRUE.equals(existingEntity.getIsReceived()) || OrderStatus.COMPLETED.name().equalsIgnoreCase(existingEntity.getOrderStatus());
        if (isAlreadyReceived) {
            throw new BusinessException("Purchase Order " + existingEntity.getOrderNo() + " is already received");
        }

        existingEntity.setIsReceived(true);
        if (existingEntity instanceof PurchaseOrder po) {
            po.complete();
        } else {
            existingEntity.setOrderStatus(OrderStatus.COMPLETED.name());
        }

        log.info("Receiving Purchase Order | id={} | orderNo={} | isReceived=true | status=COMPLETED", orderId, existingEntity.getOrderNo());
        Order savedOrder = orderRepository.save(existingEntity);

        // Fire domain event when transitioning to COMPLETED (triggers stock intake and vendor bill creation)
        eventPublisher.publishEvent(new PurchaseOrderCompletedEvent(savedOrder));

        return purchaseOrderDtoMapper.toResponseDto(savedOrder);
    }

    /**
     * Voids / cancels multiple Purchase Orders in a single bulk operation.
     */
    @Transactional
    public BulkPurchaseOrderResponseDto bulkVoidPurchaseOrders(List<UUID> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return BulkPurchaseOrderResponseDto.builder()
                    .totalRequested(0)
                    .processedCount(0)
                    .build();
        }

        UUID clientId = TenantContext.getCurrentTenant();
        List<UUID> successfulIds = new ArrayList<>();
        List<OrderResponseDto> processedOrders = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (UUID orderId : orderIds) {
            try {
                Order existingEntity = orderRepository.findByIdAndClientId(orderId, clientId)
                        .orElseThrow(() -> new ResourceNotFoundException("Purchase Order not found: " + orderId));

                if (existingEntity.getOrderType() != null && existingEntity.getOrderType() != OrderType.PURCHASE) {
                    throw new BusinessException("Order " + orderId + " is not a Purchase Order");
                }

                if (existingEntity instanceof PurchaseOrder po) {
                    po.voidOrder();
                } else {
                    if ("VOID".equalsIgnoreCase(existingEntity.getOrderStatus()) || "CANCELLED".equalsIgnoreCase(existingEntity.getOrderStatus())) {
                        throw new BusinessException("Purchase Order " + existingEntity.getOrderNo() + " is already voided");
                    }
                    existingEntity.setOrderStatus(OrderStatus.VOID.name());
                }

                existingEntity.setIsReceived(false);
                Order savedOrder = orderRepository.save(existingEntity);
                eventPublisher.publishEvent(new PurchaseOrderVoidedEvent(savedOrder));

                successfulIds.add(orderId);
                processedOrders.add(purchaseOrderDtoMapper.toResponseDto(savedOrder));
            } catch (Exception e) {
                log.warn("Bulk void failed for PO ID {}: {}", orderId, e.getMessage());
                errors.add("PO " + orderId + ": " + e.getMessage());
            }
        }

        log.info("Bulk void completed | requested={} | processed={}", orderIds.size(), successfulIds.size());
        return BulkPurchaseOrderResponseDto.builder()
                .totalRequested(orderIds.size())
                .processedCount(successfulIds.size())
                .successfulIds(successfulIds)
                .processedOrders(processedOrders)
                .errors(errors)
                .build();
    }

    /**
     * Receives/completes multiple Purchase Orders in a single bulk operation.
     */
    @Transactional
    public BulkPurchaseOrderResponseDto bulkReceivePurchaseOrders(List<UUID> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return BulkPurchaseOrderResponseDto.builder()
                    .totalRequested(0)
                    .processedCount(0)
                    .build();
        }

        UUID clientId = TenantContext.getCurrentTenant();
        List<UUID> successfulIds = new ArrayList<>();
        List<OrderResponseDto> processedOrders = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (UUID orderId : orderIds) {
            try {
                Order existingEntity = orderRepository.findByIdAndClientId(orderId, clientId)
                        .orElseThrow(() -> new ResourceNotFoundException("Purchase Order not found: " + orderId));

                if (existingEntity.getOrderType() != null && existingEntity.getOrderType() != OrderType.PURCHASE) {
                    throw new BusinessException("Order " + orderId + " is not a Purchase Order");
                }

                boolean wasCompleted = OrderStatus.COMPLETED.name().equalsIgnoreCase(existingEntity.getOrderStatus());
                if (wasCompleted) {
                    throw new BusinessException("Purchase Order " + existingEntity.getOrderNo() + " is already received/completed");
                }

                if (!StringUtils.hasText(existingEntity.getPaymentMethod())) {
                    throw new BusinessException("Payment method must be specified before receiving Purchase Order " + existingEntity.getOrderNo());
                }

                if (existingEntity instanceof PurchaseOrder po) {
                    po.complete();
                } else {
                    existingEntity.setOrderStatus(OrderStatus.COMPLETED.name());
                }

                existingEntity.setIsReceived(true);
                Order savedOrder = orderRepository.save(existingEntity);
                eventPublisher.publishEvent(new PurchaseOrderCompletedEvent(savedOrder));

                successfulIds.add(orderId);
                processedOrders.add(purchaseOrderDtoMapper.toResponseDto(savedOrder));
            } catch (Exception e) {
                log.warn("Bulk receive failed for PO ID {}: {}", orderId, e.getMessage());
                errors.add("PO " + orderId + ": " + e.getMessage());
            }
        }

        log.info("Bulk receive completed | requested={} | processed={}", orderIds.size(), successfulIds.size());
        return BulkPurchaseOrderResponseDto.builder()
                .totalRequested(orderIds.size())
                .processedCount(successfulIds.size())
                .successfulIds(successfulIds)
                .processedOrders(processedOrders)
                .errors(errors)
                .build();
    }

    private void validateCreateCommand(CreatePurchaseOrderCommand command) {
        if (command.getVendorId() == null) {
            throw new BusinessException("Vendor must be specified for a Purchase Order");
        }
        if (command.getWarehouseId() == null) {
            throw new BusinessException("Warehouse must be specified for a Purchase Order");
        }
        if (command.getLines() == null || command.getLines().isEmpty()) {
            throw new BusinessException("Purchase Order must have at least one line item");
        }
        boolean isDraft = command.getOrderStatus() == null || OrderStatus.DRAFT.name().equalsIgnoreCase(command.getOrderStatus());
        if (!isDraft && !StringUtils.hasText(command.getPaymentMethod())) {
            throw new BusinessException("Payment method must be specified for a Purchase Order");
        }
    }

    /**
     * Recalculates all line totals and order totals server-side.
     * Line Total = (Quantity x Unit Price) - Discount + Tax Amount
     * Grand Total = Sum(Line Totals)
     */
    private void recalculateTotals(Order order) {
        if (order.getLines() == null || order.getLines().isEmpty()) {
            order.setTotalAmount(BigDecimal.ZERO);
            order.setTotalTaxAmount(BigDecimal.ZERO);
            order.setTotalDiscountAmount(BigDecimal.ZERO);
            order.setGrandTotal(BigDecimal.ZERO);
            return;
        }

        BigDecimal calculatedSubtotal = BigDecimal.ZERO;
        BigDecimal calculatedTax = BigDecimal.ZERO;
        BigDecimal calculatedDiscount = BigDecimal.ZERO;
        BigDecimal calculatedGrandTotal = BigDecimal.ZERO;

        for (OrderLine line : order.getLines()) {
            BigDecimal qty = line.getQuantity() != null ? line.getQuantity() : BigDecimal.ZERO;
            BigDecimal price = line.getUnitPrice() != null ? line.getUnitPrice() : BigDecimal.ZERO;
            BigDecimal lineGross = qty.multiply(price);

            BigDecimal discount = line.getDiscountAmount() != null ? line.getDiscountAmount() : BigDecimal.ZERO;
            BigDecimal tax = line.getTaxAmount() != null ? line.getTaxAmount() : BigDecimal.ZERO;
            BigDecimal lineTotal = lineGross.subtract(discount).add(tax);

            line.setGrossLineAmount(lineGross);
            line.setLineTotal(lineTotal);

            calculatedSubtotal = calculatedSubtotal.add(lineGross);
            calculatedTax = calculatedTax.add(tax);
            calculatedDiscount = calculatedDiscount.add(discount);
            calculatedGrandTotal = calculatedGrandTotal.add(lineTotal);
        }

        order.setTotalAmount(calculatedSubtotal);
        order.setTotalTaxAmount(calculatedTax);
        order.setTotalDiscountAmount(calculatedDiscount);
        order.setGrandTotal(calculatedGrandTotal);
    }
}
