package com.restaurant.pos.credit.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class CreditCustomerDto {
    private UUID id;
    private UUID linkedCustomerId;
    private String name;
    private String phone;
    private String email;
    private String status;
    private BigDecimal creditLimit;
    private BigDecimal openingBalance;
    private BigDecimal balance;
    private BigDecimal totalCreditExtended;
    private BigDecimal paymentsReceived;
    private Long openInvoiceCount;
    private String notes;
}
