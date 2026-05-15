package com.restaurant.pos.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderLineSummaryDto {
    private UUID id;
    private UUID productId;
    private UUID variantId;
    private String productName;
    private String categoryName;
    private Boolean isPackagedGood;
    private BigDecimal quantity;
    private String unitOfMeasure;
    private BigDecimal unitPrice;
    private BigDecimal taxRate;
    private BigDecimal taxAmount;
    private BigDecimal discountAmount;
    private BigDecimal lineTotal;
}
