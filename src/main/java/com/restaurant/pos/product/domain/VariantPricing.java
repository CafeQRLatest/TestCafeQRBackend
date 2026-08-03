package com.restaurant.pos.product.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.restaurant.pos.common.entity.AuditableEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "variant_pricing")
public class VariantPricing extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Builder.Default
    private UUID id = null;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    @JsonIgnore
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_option_id")
    private VariantOption variantOption;

    private BigDecimal overridePrice;

    private BigDecimal costPrice;

    @Builder.Default
    @JsonProperty("isAvailable")
    private boolean isAvailable = true;

    @Column(name = "client_id")
    private UUID clientId;

    @Column(name = "org_id")
    private UUID orgId;
}
