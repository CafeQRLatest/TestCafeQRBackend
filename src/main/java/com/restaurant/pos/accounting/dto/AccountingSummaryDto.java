package com.restaurant.pos.accounting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountingSummaryDto {
    private LocalDateTime from;
    private LocalDateTime to;
    private long transactions;
    private BigDecimal grossSales;
    private BigDecimal discounts;
    private BigDecimal netSales;
    private BigDecimal outputTax;
    private BigDecimal inputTax;
    private BigDecimal billedTotal;
    private BigDecimal paymentCollected;
    private BigDecimal cashCollected;
    private BigDecimal onlineCollected;
    private BigDecimal upiCollected;
    private BigDecimal cardCollected;
    private BigDecimal bankCollected;
    private BigDecimal chequeCollected;
    private BigDecimal expenses;
    private BigDecimal cogsPurchases;
    private BigDecimal profit;
    private BigDecimal receivable;
    private BigDecimal payable;
    private BigDecimal inventoryValue;
    @Builder.Default
    private Map<String, BigDecimal> paymentBreakdown = new LinkedHashMap<>();
}
