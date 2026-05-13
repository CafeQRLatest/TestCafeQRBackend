package com.restaurant.pos.order.dto.report;

import com.restaurant.pos.order.dto.OrderCustomerDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderReportDto {
    private UUID id;
    private String orderNo;
    private String invoiceNo;
    private String paymentNo;
    private String orderStatus;
    private String paymentStatus;
    private String fulfillmentType;
    private String tableNumber;
    private String customerName;
    private String customerPhone;
    private List<OrderCustomerDto> customers;
    private BigDecimal totalAmount;
    private BigDecimal totalTaxAmount;
    private BigDecimal totalDiscountAmount;
    private BigDecimal grandTotal;
    private LocalDateTime orderDate;
    private LocalDateTime createdAt;
    private List<OrderLineReportDto> lines;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderLineReportDto {
        private UUID productId;
        private String productName;
        private String categoryName;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal taxRate;
        private BigDecimal taxAmount;
        private BigDecimal discountAmount;
        private BigDecimal lineTotal;
    }
}
