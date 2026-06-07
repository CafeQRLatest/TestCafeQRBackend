package com.restaurant.pos.print.dto;

import lombok.Data;

import java.util.Map;

@Data
public class PrintStationConfigurationRequest {
    private Long localRevision;
    private Integer cloudRevision;
    private Map<String, Object> settings;
}
