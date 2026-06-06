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
@Table(name = "print_stations", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"client_id", "terminal_id"})
})
public class PrintStation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "terminal_id", nullable = false)
    private UUID terminalId;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "station_token_hash", length = 64)
    private String stationTokenHash;

    @Column(name = "pairing_code_hash", length = 64)
    private String pairingCodeHash;

    @Column(name = "pairing_expires_at")
    private LocalDateTime pairingExpiresAt;

    @Column(name = "paired_at")
    private LocalDateTime pairedAt;

    @Column(name = "last_heartbeat_at")
    private LocalDateTime lastHeartbeatAt;

    @Column(name = "service_version", length = 60)
    private String serviceVersion;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "capabilities_json", columnDefinition = "TEXT")
    private String capabilitiesJson;

    @Column(name = "fallback_for_branch", nullable = false)
    @Builder.Default
    private Boolean fallbackForBranch = false;

    @Column(name = "isactive", nullable = false, length = 1)
    @Builder.Default
    private String isactive = "Y";

    public boolean isActive() {
        return "Y".equalsIgnoreCase(isactive);
    }
}
