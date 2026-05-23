package com.restaurant.pos.invoice.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
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
@EqualsAndHashCode(exclude = "invoice")
@Table(name = "invoice_lines")
public class InvoiceLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Builder.Default
    private UUID id = null;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    @JsonIgnore
    private Invoice invoice;

    @Column(name = "order_line_id")
    private UUID orderLineId;

    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "variant_id")
    private UUID variantId;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "category_name")
    private String categoryName;

    @Column(name = "is_packaged_good")
    private Boolean isPackagedGood;

    @Builder.Default
    @Column(precision = 15, scale = 3, nullable = false)
    private BigDecimal quantity = BigDecimal.ONE;

    @Builder.Default
    @Column(name = "unit_of_measure", length = 20)
    private String unitOfMeasure = "units";

    @Column(name = "unit_price", precision = 15, scale = 2, nullable = false)
    private BigDecimal unitPrice;

    @Builder.Default
    @Column(name = "tax_rate", precision = 5, scale = 2)
    private BigDecimal taxRate = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "tax_amount", precision = 15, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "discount_amount", precision = 15, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "line_total", precision = 15, scale = 2, nullable = false)
    private BigDecimal lineTotal;

    @Builder.Default
    @JsonProperty("isActive")
    @Column(name = "isactive", length = 1)
    private String isactive = "Y";

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        try {
            UUID userId = com.restaurant.pos.common.util.SecurityUtils.getCurrentUserId();
            if (userId != null) {
                if (this.createdBy == null) {
                    this.createdBy = userId;
                }
                if (this.updatedBy == null) {
                    this.updatedBy = userId;
                }
            }
        } catch (Exception ignored) {}
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        try {
            UUID userId = com.restaurant.pos.common.util.SecurityUtils.getCurrentUserId();
            if (userId != null) {
                this.updatedBy = userId;
            }
        } catch (Exception ignored) {}
    }

    public boolean isActive() {
        return "Y".equals(this.isactive);
    }

    public void deactivate() {
        this.isactive = "N";
    }
}
