package com.restaurant.pos.print.dto;

import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
public class PrintConfigurationRequest {
    private String scopeType;
    private UUID scopeId;
    private UUID orgId;
    private Map<String, Object> settings;
}
