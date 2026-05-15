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
@Table(name = "party_ledger_entries")
public class PartyLedgerEntry extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Builder.Default
    private UUID id = null;

    @Enumerated(EnumType.STRING)
    @Column(name = "party_type", nullable = false, length = 20)
    private PartyType partyType;

    @Column(name = "party_id", nullable = false)
    private UUID partyId;

    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "journal_entry_id")
    private UUID journalEntryId;

    @Column(name = "source_type", length = 50)
    private String sourceType;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "entry_date", nullable = false)
    @Builder.Default
    private LocalDateTime entryDate = LocalDateTime.now();

    @Column(precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal debit = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal credit = BigDecimal.ZERO;

    @Column(name = "balance_after", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal balanceAfter = BigDecimal.ZERO;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Builder.Default
    @JsonProperty("isActive")
    @Column(name = "isactive", length = 1)
    private String isactive = "Y";
}
