package com.restaurant.pos.accounting.dto;

import lombok.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountingMappingsDto {

    @Builder.Default
    private Map<String, UUID> accountMappings = new LinkedHashMap<>();

    @Builder.Default
    private Map<String, UUID> paymentMethodMappings = new LinkedHashMap<>();
}
