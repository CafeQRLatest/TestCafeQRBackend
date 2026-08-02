package com.restaurant.pos.credit.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class CreditReportDto {
    private BigDecimal creditExtended;
    private BigDecimal paymentsReceived;
    private BigDecimal outstanding;
    private BigDecimal outputTax;
    private Long orderCount;
    private Long customerCount;
    private List<CreditOrderDto> orders;
    private List<PaymentTransactionDto> payments;

    @Data
    @Builder
    public static class PaymentTransactionDto {
        private UUID paymentId;
        private UUID creditCustomerId;
        private String customerName;
        private String customerPhone;
        private LocalDateTime transactionDate;
        private String type;
        private String paymentType;
        private String paymentTypeLabel;
        private String paymentMethod;
        private BigDecimal amount;
        private BigDecimal roundOffAmount;
        private BigDecimal taxAmount;
        private BigDecimal subtotal;
        private BigDecimal grossAmount;
        private BigDecimal discountAmount;
        private String description;
        private String referenceNo;
        private UUID orderId;
        private String orderNo;
        private UUID invoiceId;
        private String invoiceNo;
    }
}
