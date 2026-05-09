package com.restaurant.pos.order.dto;

import lombok.Data;

@Data
public class OrderCancelRequest {
    private String reason;
}
