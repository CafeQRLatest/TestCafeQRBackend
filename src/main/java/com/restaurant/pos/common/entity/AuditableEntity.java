package com.restaurant.pos.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@MappedSuperclass
public abstract class AuditableEntity {

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        
        String currentUser = resolveCurrentUser();
        
        // Only set if not already manually set
        if (this.createdBy == null) {
            this.createdBy = currentUser;
        }
        if (this.updatedBy == null) {
            this.updatedBy = currentUser;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        this.updatedBy = resolveCurrentUser();
    }

    private String resolveCurrentUser() {
        try {
            java.util.UUID userId = com.restaurant.pos.common.util.SecurityUtils.getCurrentUserId();
            if (userId != null) {
                return userId.toString();
            }
        } catch (Exception ignored) {
            // Fall through to SYSTEM
        }
        return "SYSTEM";
    }
}
