package com.restaurant.pos.accounting.dto;

import com.restaurant.pos.accounting.domain.AccountType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountingAccountPeriodDto {
    private UUID id;
    private String code;
    private String name;
    private AccountType accountType;
    private String accountSubType;
    private String systemKey;
    private Boolean cashAccount;
    private Boolean bankAccount;
    private String isActive;
    private BigDecimal openingBalance;
    private BigDecimal periodDebit;
    private BigDecimal periodCredit;
    private BigDecimal periodNet;
    private BigDecimal periodOpening;
    private BigDecimal periodClosing;
}
