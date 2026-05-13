package com.restaurant.pos.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCustomerDto {
    private UUID id;
    private String name;
    private String phone;
    private boolean primary;
}
