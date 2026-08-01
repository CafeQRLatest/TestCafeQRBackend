package com.restaurant.pos.inventory.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "stock_adjustment_lines")
public class StockAdjustmentLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Builder.Default
    private UUID id = null;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "adjustment_id")
    @JsonIgnore
    @ToString.Exclude
    private StockAdjustment adjustment;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "variant_id")
    private UUID variantId;

    @Column(name = "quantity_change", nullable = false, precision = 15, scale = 3)
    private BigDecimal quantityChange;

    @Builder.Default
    @Column(name = "unit_cost", precision = 15, scale = 2)
    private BigDecimal unitCost = BigDecimal.ZERO;

    @Transient
    private String productName;

    @Transient
    private String variantName;

    @Transient
    private String sku;

    @Transient
    private String categoryName;

    @Builder.Default
    @JsonProperty("isActive")
    @Column(name = "isactive", length = 1)
    private String isActive = "Y";
}



