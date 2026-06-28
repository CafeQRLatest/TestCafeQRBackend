package com.restaurant.pos.subscription.dto;

import com.restaurant.pos.subscription.domain.ModuleName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPaymentRequest {
    private String billingCycle; // MONTHLY, ANNUAL
    private List<ModuleName> modules; // Modules being purchased or upgraded
    private String setupOption; // DIY, WHITE_GLOVE
    private UUID orgId; // Optional branch/organization ID
}
