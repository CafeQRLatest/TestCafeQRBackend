package com.restaurant.pos.accounting.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.restaurant.pos.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
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
@Table(name = "journal_entries")
public class JournalEntry extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Builder.Default
    private UUID id = null;

    @Column(name = "entry_no", nullable = false, length = 80)
    private String entryNo;

    @Column(name = "entry_date", nullable = false)
    @Builder.Default
    private LocalDateTime entryDate = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private JournalStatus status = JournalStatus.POSTED;

    @Column(name = "source_type", length = 50)
    private String sourceType;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "terminal_id")
    private UUID terminalId;

    @Column(name = "warehouse_id")
    private UUID warehouseId;

    @Column(name = "currency_id")
    private UUID currencyId;

    @Column(name = "total_debit", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalDebit = BigDecimal.ZERO;

    @Column(name = "total_credit", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalCredit = BigDecimal.ZERO;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Builder.Default
    @JsonProperty("isActive")
    @Column(name = "isactive", length = 1)
    private String isactive = "Y";

    @OneToMany(mappedBy = "journalEntry", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<JournalLine> lines = new ArrayList<>();

    public void attachLine(JournalLine line) {
        lines.add(line);
        line.setJournalEntry(this);
    }
}
