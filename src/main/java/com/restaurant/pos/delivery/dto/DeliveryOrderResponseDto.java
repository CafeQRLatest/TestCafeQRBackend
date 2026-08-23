package com.restaurant.pos.delivery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryOrderResponseDto {
    private UUID id;
    private String orderNo;
    private UUID clientId;
    private UUID orgId;
    private String orderStatus;
    private String paymentStatus;
    private String orderSource;
    private String fulfillmentType;
    private String description;
    private String remarks;
    private String reference;
    private Instant orderDate;
    private BigDecimal totalTaxableAmount;
    private BigDecimal totalTaxAmount;
    private BigDecimal totalGrossAmount;
    private BigDecimal grandTotal;
    private String customerName;
    private String customerEmail;
    private String customerPhone;
    private String deliveryAddress;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private List<Map<String, Object>> items;
}
