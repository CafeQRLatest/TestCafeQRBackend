package com.restaurant.pos.print.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class CreatePrintJobRequest {
    private UUID orderId;
    private String jobKind;
}
