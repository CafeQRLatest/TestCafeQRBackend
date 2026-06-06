package com.restaurant.pos.print.domain;

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
@Table(name = "print_configurations")
public class PrintConfiguration extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "scope_type", nullable = false, length = 30)
    private String scopeType;

    @Column(name = "scope_id")
    private UUID scopeId;

    @Column(nullable = false)
    @Builder.Default
    private Integer revision = 1;

    @Column(name = "settings_json", nullable = false, columnDefinition = "TEXT")
    @Builder.Default
    private String settingsJson = "{}";
}
