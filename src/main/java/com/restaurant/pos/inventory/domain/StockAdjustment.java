package com.restaurant.pos.inventory.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.restaurant.pos.common.entity.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "stock_adjustments")
public class StockAdjustment extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Builder.Default
    private UUID id = null;

    @Column(name = "client_id")
    private UUID clientId;

    @Column(name = "org_id")
    private UUID orgId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "adjustment_number", unique = true, nullable = false, length = 50)
    private String adjustmentNumber;

    @Builder.Default
    @Column(name = "adjustment_date")
    private LocalDateTime adjustmentDate = LocalDateTime.now();

    @Column(nullable = false, length = 50)
    private String reason; // WASTAGE, DAMAGE, AUDIT, EXPIRY

    @Builder.Default
    @Column(length = 20)
    private String status = "DRAFT"; // DRAFT, COMPLETED

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Transient
    private String createdByName;

    @Transient
    private String updatedByName;

    @Builder.Default
    @JsonProperty("isActive")
    @Column(name = "isactive", length = 1)
    private String isactive = "Y";

    @OneToMany(mappedBy = "adjustment", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @BatchSize(size = 20)
    private List<StockAdjustmentLine> lines = new ArrayList<>();

    public void addLine(StockAdjustmentLine line) {
        lines.add(line);
        line.setAdjustment(this);
    }
}
