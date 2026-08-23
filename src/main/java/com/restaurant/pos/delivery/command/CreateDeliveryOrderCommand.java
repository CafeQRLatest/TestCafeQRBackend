package com.restaurant.pos.delivery.command;

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
public class CreateDeliveryOrderCommand {
    private UUID clientId;
    private String orgId;
    private String fulfillmentType; // DELIVERY, TAKEAWAY
    private String customerEmail;
    private String customerName;
    private String customerPhone;
    private String deliveryAddress;
    private String note;
    private String remarks;
    private String paymentMethod; // COD, ONLINE, RAZORPAY
    private String razorpayPaymentId;
    private String razorpayOrderId;
    private String razorpaySignature;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private List<Map<String, Object>> items;
}
