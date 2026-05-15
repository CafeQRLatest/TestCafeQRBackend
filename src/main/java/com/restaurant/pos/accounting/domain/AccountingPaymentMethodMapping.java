package com.restaurant.pos.accounting.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.restaurant.pos.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "accounting_payment_method_mappings", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"client_id", "org_id", "payment_method"})
})
public class AccountingPaymentMethodMapping extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Builder.Default
    private UUID id = null;

    @Column(name = "payment_method", nullable = false, length = 50)
    private String paymentMethod;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Builder.Default
    @JsonProperty("isActive")
    @Column(name = "isactive", length = 1)
    private String isactive = "Y";
}
