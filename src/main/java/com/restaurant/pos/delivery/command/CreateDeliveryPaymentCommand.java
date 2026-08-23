package com.restaurant.pos.delivery.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDeliveryPaymentCommand {
    private UUID clientId;
    private String orgId;
    private String customerPhone;
    private String customerEmail;
    private List<Map<String, Object>> items;
}
