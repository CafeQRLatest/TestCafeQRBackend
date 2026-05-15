package com.restaurant.pos.accounting.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.restaurant.pos.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "accounting_accounts", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"client_id", "org_id", "code"})
})
public class AccountingAccount extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Builder.Default
    private UUID id = null;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 30)
    private AccountType accountType;

    @Column(name = "account_sub_type", length = 60)
    private String accountSubType;

    @Column(name = "currency_id")
    private UUID currencyId;

    @Column(name = "opening_balance", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal openingBalance = BigDecimal.ZERO;

    @Column(name = "current_balance", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal currentBalance = BigDecimal.ZERO;

    @Column(name = "is_system_account")
    @Builder.Default
    private Boolean systemAccount = false;

    @Column(name = "is_cash_account")
    @Builder.Default
    private Boolean cashAccount = false;

    @Column(name = "is_bank_account")
    @Builder.Default
    private Boolean bankAccount = false;

    @Column(name = "bank_name", length = 160)
    private String bankName;

    @Column(name = "account_number", length = 100)
    private String accountNumber;

    @Column(name = "ifsc_code", length = 40)
    private String ifscCode;

    @Column(name = "upi_id", length = 120)
    private String upiId;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Builder.Default
    @JsonProperty("isActive")
    @Column(name = "isactive", length = 1)
    private String isactive = "Y";
}
