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
public class TrialBalanceRowDto {
    private UUID accountId;
    private String code;
    private String name;
    private AccountType accountType;
    private BigDecimal debit;
    private BigDecimal credit;
    private BigDecimal balance;
}
