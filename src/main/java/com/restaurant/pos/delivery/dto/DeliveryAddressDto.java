package com.restaurant.pos.delivery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryAddressDto {
    private UUID id;
    private UUID clientId;
    private String email;
    private String label;
    private String fullAddress;
    private String landmark;
    private BigDecimal latitude;
    private BigDecimal longitude;
}
