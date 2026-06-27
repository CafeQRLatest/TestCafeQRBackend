package com.restaurant.pos.purchasing.domain;

import com.restaurant.pos.common.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "payment_types")
public class PaymentType extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID id;

    /**
     * User-facing label shown in dropdowns across Sales, Purchase & Expense forms.
     * E.g. "Cash", "UPI — GPay", "HDFC Credit Card"
     */
    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Builder.Default
    @Column(name = "payment_type", nullable = false, length = 20)
    private String paymentType = "OTHERS";

    @Builder.Default
    @Column(name = "sales", nullable = false, length = 1)
    private String sales = "Y";

    @Builder.Default
    @Column(name = "purchase", nullable = false, length = 1)
    private String purchase = "Y";

    @Builder.Default
    @Column(name = "expense", nullable = false, length = 1)
    private String expense = "Y";

    /**
     * Optional accounting General Ledger reference code for integration.
     * E.g. "CASH_ACCT_001", "BANK_HDFC"
     */
    @Column(name = "ledger_ref", length = 80)
    private String ledgerRef;

    /**
     * When true, this is the default payment type for its applicable contexts.
     */
    @Builder.Default
    @Column(name = "is_default")
    private Boolean isDefault = false;

    /**
     * Display order in dropdowns — lower numbers appear first.
     */
    @Builder.Default
    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    /**
     * Optional free-text notes about this payment type.
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    @Builder.Default
    @JsonProperty("isActive")
    @Column(name = "isactive", length = 1)
    private String isactive = "Y";
}
