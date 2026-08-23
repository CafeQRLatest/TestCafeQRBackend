package com.restaurant.pos.delivery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryRestaurantSettingsDto {
    private UUID clientId;
    private UUID orgId;
    private String restaurantName;
    private String logoUrl;
    private String bannerUrl;
    private String brandColor;
    private String address;
    private String phone;
    private String whatsappNumber;
    private String currency;
    private String timezone;
    private String googleMapsUrl;
    private String instagramUrl;
    private String facebookUrl;
    private Boolean deliveryEnabled;
    private Boolean takeawayEnabled;
    private BigDecimal minOrderAmount;
    private Integer estimatedDeliveryMinutes;
    private Boolean taxEnabled;
    private String taxLabelGlobal;
    private List<Object> taxRates;
    private String taxDefaultId;
    private Boolean pricesIncludeTax;
    private Boolean taxSplitEnabled;
    private Integer currencyDecimalPlaces;
    private Boolean onlinePaymentEnabled;
    private String razorpayKeyId;
    private String posType;
    private Double deliveryRadiusKm;
    private Double branchLatitude;
    private Double branchLongitude;
}
