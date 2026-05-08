package com.restaurant.pos.client.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.restaurant.pos.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Table(name = "terminals")
public class Terminal extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", insertable = false, updatable = false)
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_id", insertable = false, updatable = false)
    private Organization organization;

    @Column(nullable = false)
    private String name;

    @Column(name = "terminal_code", unique = true)
    private String terminalCode;

    @Column(name = "device_type")
    private String deviceType;

    @Column(name = "ip_address")
    private String ipAddress;

    @Builder.Default
    @Column(name = "isactive", length = 1)
    @com.fasterxml.jackson.annotation.JsonProperty("isactive")
    private String isactive = "Y"; // Standardized Y/N

    @Column(name = "device_id")
    private UUID deviceId;

    @Column(name = "offline_billing_mode", length = 30)
    @Builder.Default
    private String offlineBillingMode = "NONE";

    @Column(name = "offline_billing_enabled")
    @Builder.Default
    private Boolean offlineBillingEnabled = false;

    @Column(name = "print_station_enabled")
    @Builder.Default
    private Boolean printStationEnabled = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", insertable = false, updatable = false)
    private Device device;

    public boolean isActive() {
        return "Y".equalsIgnoreCase(isactive);
    }

    public void setActive(boolean active) {
        this.isactive = active ? "Y" : "N";
    }
}
