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
public class HourlySalesDto {
    private int hour;
    private String hourLabel;
    private long orderCount;
    private BigDecimal totalAmount;
}
