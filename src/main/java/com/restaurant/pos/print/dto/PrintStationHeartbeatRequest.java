package com.restaurant.pos.print.dto;

import lombok.Data;

@Data
public class PrintStationHeartbeatRequest {
    private String serviceVersion;
    private Object capabilities;
    private Integer queueDepth;
    private String serviceStatus;
}
