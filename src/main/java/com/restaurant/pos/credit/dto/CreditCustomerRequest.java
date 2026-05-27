package com.restaurant.pos.credit.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class CreditCustomerRequest {
    private UUID linkedCustomerId;
    private String name;
    private String phone;
    private String email;
    private BigDecimal creditLimit;
    private BigDecimal openingBalance;
    private String notes;
}
