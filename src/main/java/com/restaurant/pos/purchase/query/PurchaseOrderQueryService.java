package com.restaurant.pos.purchase.query;

import com.restaurant.pos.common.exception.BusinessException;
import com.restaurant.pos.common.exception.ResourceNotFoundException;
import com.restaurant.pos.common.tenant.TenantContext;
import com.restaurant.pos.order.domain.Order;
import com.restaurant.pos.order.domain.OrderType;
import com.restaurant.pos.order.dto.OrderResponseDto;
import com.restaurant.pos.order.repository.OrderRepository;
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
 * Handles read-only operations using PurchaseOrderSpecifications builder.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseOrderQueryService {

    private final OrderRepository orderRepository;
    private final PurchaseOrderDtoMapper purchaseOrderDtoMapper;

    @Transactional(readOnly = true)
    public Page<OrderResponseDto> searchPurchaseOrders(PurchaseOrderSearchRequest request, Pageable pageable) {
        UUID clientId = TenantContext.getCurrentTenant();
        UUID orgId = request.getBranchId() != null ? request.getBranchId() : TenantContext.getCurrentOrg();

        Specification<Order> spec = PurchaseOrderSpecifications.withFilters(request, clientId, orgId);
        Page<Order> pageResult = orderRepository.findAll(spec, pageable);
        return pageResult.map(purchaseOrderDtoMapper::toResponseDto);
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

    @Transactional(readOnly = true)
    public OrderResponseDto getPurchaseOrder(UUID orderId) {
        UUID clientId = TenantContext.getCurrentTenant();
        Order order = orderRepository.findByIdAndClientId(orderId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order not found for ID: " + orderId));

        if (order.getOrderType() != null && order.getOrderType() != OrderType.PURCHASE) {
            throw new BusinessException("Order " + orderId + " is not a Purchase Order");
        }

        return purchaseOrderDtoMapper.toResponseDto(order);
    }
}
