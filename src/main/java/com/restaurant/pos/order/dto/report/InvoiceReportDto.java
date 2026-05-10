package com.restaurant.pos.order.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceReportDto {
    private UUID id;
    private UUID orderId;
    private String invoiceNo;
    private String invoiceType;
    private String status;
    private String docStatus;
    private BigDecimal totalAmount;
    private BigDecimal amountDue;
    private String customerName;
    private String paymentMethod;
    private String paymentNo;
    private LocalDateTime invoiceDate;
    private String description;
    private String reference;
    // Void tracking
    private UUID originalInvoiceId;
}
