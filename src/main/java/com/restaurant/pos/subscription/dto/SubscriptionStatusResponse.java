package com.restaurant.pos.subscription.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionStatusResponse {
    private boolean active;
    private String status;
    private int daysLeft;
    private LocalDateTime expiryDate;
    private String message;
    private java.util.List<com.restaurant.pos.subscription.domain.ModuleName> activeModules;
}
