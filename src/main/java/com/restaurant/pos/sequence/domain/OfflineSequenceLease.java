package com.restaurant.pos.sequence.domain;

import com.restaurant.pos.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "offline_sequence_leases", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"client_id", "lease_key"})
})
public class OfflineSequenceLease extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "terminal_id", nullable = false)
    private UUID terminalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", length = 50, nullable = false)
    private DocumentType documentType;

    @Column(name = "start_number", nullable = false)
    private Long startNumber;

    @Column(name = "end_number", nullable = false)
    private Long endNumber;

    @Column(name = "next_number", nullable = false)
    private Long nextNumber;

    @Column(length = 60)
    private String prefix;

    @Column(length = 60)
    private String suffix;

    @Column(name = "padding_length", nullable = false)
    private Integer paddingLength;

    @Column(length = 30, nullable = false)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "lease_key", length = 180, nullable = false)
    private String leaseKey;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
}
