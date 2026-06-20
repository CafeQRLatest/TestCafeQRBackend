package com.restaurant.pos.push.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PushPreferencesRequest {
    @NotBlank(message = "Device token is required")
    private String deviceToken;

    private Boolean notifyKitchen;
    private Boolean notifyTakeaway;
    private Boolean notifyDelivery;
    private Boolean notifySettled;
}
