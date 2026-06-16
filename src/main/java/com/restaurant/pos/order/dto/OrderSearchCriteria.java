package com.restaurant.pos.order.dto;

import com.restaurant.pos.order.domain.OrderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderSearchCriteria {
    private OrderType orderType;
    private Instant fromDate;
    private Instant toDate;
    private String status;
    private UUID branchId;
    private UUID vendorId;
    private UUID customerId;
    private UUID warehouseId;
    private String paymentMethod;
    private String searchTerm;
    private UUID terminalId;
}
