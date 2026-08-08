package com.restaurant.pos.purchase.query;

import com.restaurant.pos.common.exception.BusinessException;
import com.restaurant.pos.common.exception.ResourceNotFoundException;
import com.restaurant.pos.common.tenant.TenantContext;
import com.restaurant.pos.order.domain.Order;
import com.restaurant.pos.order.domain.OrderType;
import com.restaurant.pos.order.dto.OrderResponseDto;
import com.restaurant.pos.order.repository.OrderRepository;
import com.restaurant.pos.purchase.dto.PurchaseOrderSummaryDto;
import com.restaurant.pos.purchase.mapper.PurchaseOrderDtoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * CQRS Query Service for Purchase Orders.
 * searchPurchaseOrders() returns lightweight PurchaseOrderSummaryDto (no lines, no N+1 user/invoice queries).
 * getPurchaseOrder(id) returns the full OrderResponseDto with lines and all detail for the popup.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseOrderQueryService {

    private final OrderRepository orderRepository;
    private final PurchaseOrderDtoMapper purchaseOrderDtoMapper;
    private final com.restaurant.pos.order.service.OrderService orderService;

    /**
     * Paginated purchase order history list mapped to lightweight PurchaseOrderSummaryDto.
     * Uses composite indexes and Specification filters to fetch orders cleanly.
     * Line items and lazy sub-queries are skipped for history listing.
     */
    @Transactional(readOnly = true)
    public Page<PurchaseOrderSummaryDto> searchPurchaseOrders(PurchaseOrderSearchRequest request, Pageable pageable) {
        UUID clientId = TenantContext.getCurrentTenant();
        UUID orgId = request.getBranchId() != null 
                ? request.getBranchId() 
                : (com.restaurant.pos.common.util.SecurityUtils.isSuperAdmin() ? null : TenantContext.getCurrentOrg());

        Specification<Order> spec = PurchaseOrderSpecifications.withFilters(request, clientId, orgId);
        Page<Order> pageResult = orderRepository.findAll(spec, pageable);
        return pageResult.map(purchaseOrderDtoMapper::toSummaryDto);
    }

    @Transactional(readOnly = true)
    public List<OrderResponseDto> getDraftPurchaseOrders() {
        UUID clientId = TenantContext.getCurrentTenant();
        UUID orgId = TenantContext.getCurrentOrg();

        List<Order> drafts = (orgId != null)
                ? orderRepository.findByClientIdAndOrgIdAndOrderTypeOrderByCreatedAtDesc(clientId, orgId, OrderType.PURCHASE)
                : orderRepository.findByClientIdAndOrderTypeOrderByCreatedAtDesc(clientId, OrderType.PURCHASE);

        return drafts.stream()
                .filter(o -> "DRAFT".equalsIgnoreCase(o.getOrderStatus()))
                .map(purchaseOrderDtoMapper::toResponseDto)
                .toList();
    }

    /**
     * Returns the FULL purchase order DTO with line items, user names, and linked invoice/payment refs.
     * Called when the user opens a specific order in the detail popup.
     */
    @Transactional(readOnly = true)
    public OrderResponseDto getPurchaseOrder(UUID orderId) {
        UUID clientId = TenantContext.getCurrentTenant();
        UUID orgId = TenantContext.getCurrentOrg();

        Order order = (orgId != null)
                ? orderRepository.findByIdAndClientIdAndOrgId(orderId, clientId, orgId)
                    .orElseGet(() -> orderRepository.findByIdAndClientId(orderId, clientId)
                        .orElseThrow(() -> new ResourceNotFoundException("Purchase Order not found for ID: " + orderId)))
                : orderRepository.findByIdAndClientId(orderId, clientId)
                    .orElseThrow(() -> new ResourceNotFoundException("Purchase Order not found for ID: " + orderId));

        if (order.getOrderType() != null && order.getOrderType() != OrderType.PURCHASE) {
            throw new BusinessException("Order " + orderId + " is not a Purchase Order");
        }

        return purchaseOrderDtoMapper.toResponseDto(order);
    }

    /**
     * Returns revision history for a purchase order (current + VOID predecessors).
     */
    @Transactional(readOnly = true)
    public List<OrderResponseDto> getPurchaseOrderRevisions(UUID orderId) {
        UUID clientId = TenantContext.getCurrentTenant();
        Order current = orderRepository.findByIdAndClientId(orderId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order not found for ID: " + orderId));

        String baseOrderNo = current.getOrderNo();
        if (baseOrderNo != null && baseOrderNo.contains("_VOID_")) {
            baseOrderNo = baseOrderNo.substring(0, baseOrderNo.indexOf("_VOID_"));
        }
        String voidPrefix = baseOrderNo + "_VOID_%";

        return orderRepository.findAllRevisionsByOrderNo(clientId, baseOrderNo, voidPrefix)
                .stream()
                .map(purchaseOrderDtoMapper::toResponseDto)
                .toList();
    }

    /**
     * Returns payment splits for a settled mixed payment purchase order.
     */
    @Transactional(readOnly = true)
    public List<com.restaurant.pos.order.dto.PaymentSplitResponseDto> getPaymentSplits(UUID orderId) {
        return orderService.getPaymentSplits(orderId).stream()
                .map(ps -> new com.restaurant.pos.order.dto.PaymentSplitResponseDto(ps.getId(), ps.getPaymentMethod(), ps.getAmount(), ps.getReferenceNo()))
                .toList();
    }
}
