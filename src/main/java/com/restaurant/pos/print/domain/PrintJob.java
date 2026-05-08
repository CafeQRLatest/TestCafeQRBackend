package com.restaurant.pos.print.domain;

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
@Table(name = "print_jobs", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"client_id", "dedupe_key"})
})
public class PrintJob extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "offline_operation_id", length = 160)
    private String offlineOperationId;

    @Column(name = "source_operation_id", length = 160)
    private String sourceOperationId;

    @Column(name = "source_terminal_id")
    private UUID sourceTerminalId;

    @Column(name = "source_device_id")
    private UUID sourceDeviceId;

    @Column(name = "target_terminal_id")
    private UUID targetTerminalId;

    @Column(name = "claimed_by_terminal_id")
    private UUID claimedByTerminalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_kind", length = 30, nullable = false)
    private PrintJobKind jobKind;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    @Builder.Default
    private PrintJobStatus status = PrintJobStatus.PENDING;

    @Column(name = "dedupe_key", length = 220, nullable = false)
    private String dedupeKey;

    @Column(name = "payload_json", columnDefinition = "TEXT", nullable = false)
    private String payloadJson;

    @Column(nullable = false)
    @Builder.Default
    private Integer attempts = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    @Column(name = "printed_at")
    private LocalDateTime printedAt;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;
}
