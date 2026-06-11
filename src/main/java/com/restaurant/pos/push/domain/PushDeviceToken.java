package com.restaurant.pos.push.domain;

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
@Table(name = "push_device_tokens")
public class PushDeviceToken extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "device_token", length = 512, nullable = false, unique = true)
    private String deviceToken;

    @Column(length = 50)
    private String platform;

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

    @Builder.Default
    @Column(name = "notify_kitchen", nullable = false)
    private boolean notifyKitchen = true;

    @Builder.Default
    @Column(name = "notify_takeaway", nullable = false)
    private boolean notifyTakeaway = true;

    @Builder.Default
    @Column(name = "notify_delivery", nullable = false)
    private boolean notifyDelivery = true;

    @Builder.Default
    @Column(name = "notify_settled", nullable = false)
    private boolean notifySettled = true;
}
