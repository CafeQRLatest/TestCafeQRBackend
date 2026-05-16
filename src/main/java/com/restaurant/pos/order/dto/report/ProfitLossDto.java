package com.restaurant.pos.order.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfitLossDto {
    private BigDecimal grossSales;
    private BigDecimal discounts;
    private BigDecimal netSales;
    private BigDecimal totalTax;
    private BigDecimal inputTax;
    private BigDecimal cogsPurchases;
    private BigDecimal operatingExpenses;
    private BigDecimal totalExpenses;
    private BigDecimal netProfit;
    private BigDecimal creditOutstanding;
    private BigDecimal netCashProfit;
    private String basis;
}
