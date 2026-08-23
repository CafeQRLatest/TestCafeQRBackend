package com.restaurant.pos.delivery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryMenuItemDto {
    private UUID id;
    private String name;
    private String description;
    private BigDecimal price;
    private String imageUrl;
    private String category;
    private Boolean isVeg;
    private Boolean isAvailable;
    private String productType;
    private BigDecimal taxRate;
    private Boolean isPackagedGood;
    private Boolean hasVariants;
    private List<Map<String, Object>> variantMappings;
    private List<Map<String, Object>> variantPricings;
}
