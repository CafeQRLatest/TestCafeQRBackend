package com.restaurant.pos.order.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class OrderSettleRequest {
    private String paymentMethod;
    private BigDecimal cashAmount;
    private BigDecimal onlineAmount;
    private BigDecimal amountPaid;
    private BigDecimal discountAmount;
    private BigDecimal roundOffAmount;
    private String description;
    private List<PaymentSplitRequest> paymentSplits;

    @Data
    public static class PaymentSplitRequest {
        private String paymentMethod;
        private BigDecimal amount;
        private String referenceNo;
    }
}
