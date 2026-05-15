package com.restaurant.pos.accounting.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.restaurant.pos.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "payment_allocations")
public class PaymentAllocation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Builder.Default
    private UUID id = null;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "invoice_id")
    private UUID invoiceId;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "allocated_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal allocatedAmount;

    @Column(name = "allocation_date", nullable = false)
    @Builder.Default
    private LocalDateTime allocationDate = LocalDateTime.now();

    @Column(length = 20)
    @Builder.Default
    private String status = "POSTED";

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Builder.Default
    @JsonProperty("isActive")
    @Column(name = "isactive", length = 1)
    private String isactive = "Y";
}
