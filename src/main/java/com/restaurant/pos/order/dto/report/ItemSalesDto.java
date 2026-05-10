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
public class ItemSalesDto {
    private String productName;
    private String categoryName;
    private BigDecimal quantitySold;
    private BigDecimal revenue;
}
