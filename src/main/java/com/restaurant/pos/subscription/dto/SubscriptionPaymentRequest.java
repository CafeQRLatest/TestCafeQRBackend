package com.restaurant.pos.subscription.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPaymentRequest {
    private boolean includeBasePlan;
    private boolean includeSetupService;
    private List<String> selectedModules;
    private UUID orgId;
}
