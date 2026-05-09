package com.restaurant.pos.order.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class OrderMoveTableRequest {
    private UUID tableId;
    private String tableNumber;
}
