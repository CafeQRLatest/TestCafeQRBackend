package com.restaurant.pos.push.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PushPreferencesRequest {
    @NotBlank(message = "Device token is required")
    private String deviceToken;

    private boolean notifyKitchen;
    private boolean notifyTakeaway;
    private boolean notifyDelivery;
    private boolean notifySettled;
}
