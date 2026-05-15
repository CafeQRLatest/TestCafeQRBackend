package com.restaurant.pos.order.controller;

import com.restaurant.pos.common.dto.ApiResponse;
import com.restaurant.pos.order.domain.Order;
import com.restaurant.pos.order.domain.OrderType;
import com.restaurant.pos.order.dto.OrderCancelRequest;
import com.restaurant.pos.order.dto.OrderMoveTableRequest;
import com.restaurant.pos.order.dto.OrderSettleRequest;
import com.restaurant.pos.order.dto.OrderSummaryDto;
import com.restaurant.pos.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
 
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<Order>>> getOrders(@RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getOrders(status)));
    }  

    @GetMapping("/type/{type}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<Order>>> getOrdersByType(@PathVariable String type) {
        try {
            OrderType orderType = OrderType.valueOf(type.toUpperCase());
            return ResponseEntity.ok(ApiResponse.success(orderService.getOrdersByType(orderType)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Invalid order type: " + type + ". Valid values: SALE, PURCHASE, EXPENSE"));
        }
    }

    @GetMapping("/sales/live")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<OrderSummaryDto>>> getLiveSalesOrders() {
        return ResponseEntity.ok(ApiResponse.success(orderService.getLiveSalesOrders()));
    }

    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Page<OrderSummaryDto>>> getOrderHistory(
            @RequestParam(required = false) OrderType type,
            @RequestParam(required = false) Instant fromDate,
            @RequestParam(required = false) Instant toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        OrderType requestedType = type == null ? OrderType.SALE : type;
        if (requestedType != OrderType.SALE) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Only SALE order history is available here"));
        }
        return ResponseEntity.ok(ApiResponse.success(orderService.getSalesOrderHistory(fromDate, toDate, page, size)));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<Order>>> searchOrders(
            @RequestParam(required = false) OrderType type,
            @RequestParam(required = false) java.time.Instant fromDate,
            @RequestParam(required = false) java.time.Instant toDate,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) java.util.UUID branchId,
            @RequestParam(required = false) java.util.UUID vendorId,
            @RequestParam(required = false) java.util.UUID customerId,
            @RequestParam(required = false) String searchTerm
    ) {
        com.restaurant.pos.order.dto.OrderSearchCriteria criteria = com.restaurant.pos.order.dto.OrderSearchCriteria.builder()
                .orderType(type)
                .fromDate(fromDate)
                .toDate(toDate)
                .status(status)
                .branchId(branchId)
                .vendorId(vendorId)
                .customerId(customerId)
                .searchTerm(searchTerm)
                .build();
        return ResponseEntity.ok(ApiResponse.success(orderService.searchOrders(criteria)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Order>> getOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getOrder(id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Order>> createOrder(@RequestBody Order order) {
        return ResponseEntity.ok(ApiResponse.success(orderService.createOrder(order)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Order>> updateOrder(@PathVariable UUID id, @RequestBody Order order) {
        return ResponseEntity.ok(ApiResponse.success(orderService.updateOrder(id, order)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Order>> updateOrderStatus(
            @PathVariable UUID id,
            @RequestParam String status,
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(required = false) String description
    ) {
        return ResponseEntity.ok(ApiResponse.success(orderService.updateOrderStatus(id, status, paymentStatus, description)));
    }

    @PostMapping("/{id}/bill")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Order>> billOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(orderService.billOrder(id)));
    }

    @PostMapping("/{id}/settle")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Order>> settleOrder(@PathVariable UUID id, @RequestBody(required = false) OrderSettleRequest request) {
        return ResponseEntity.ok(ApiResponse.success(orderService.settleOrder(id, request)));
    }

    @PostMapping("/{id}/move-table")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Order>> moveTable(@PathVariable UUID id, @RequestBody OrderMoveTableRequest request) {
        return ResponseEntity.ok(ApiResponse.success(orderService.moveTable(id, request)));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Order>> cancelOrder(@PathVariable UUID id, @RequestBody(required = false) OrderCancelRequest request) {
        return ResponseEntity.ok(ApiResponse.success(orderService.cancelOrder(id, request)));
    }
}
