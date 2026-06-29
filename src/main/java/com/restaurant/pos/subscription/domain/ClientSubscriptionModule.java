package com.restaurant.pos.subscription.domain;

import com.restaurant.pos.common.entity.AuditableEntity;
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
@Table(name = "client_subscription_modules")
public class ClientSubscriptionModule extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "org_id")
    private UUID orgId; // Nullable for account-level modules

    @Enumerated(EnumType.STRING)
    @Column(name = "module_name", nullable = false)
    private ModuleName moduleName;

    @Column(nullable = false)
    private String status; // ACTIVE, EXPIRED, TRIAL, CANCEL_PENDING

    @Column(name = "auto_renew", nullable = false)
    private boolean autoRenew;

    @Column(name = "expiry_date", nullable = false)
    private LocalDateTime expiryDate;
}
