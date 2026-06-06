package com.restaurant.pos.print.dto;

import lombok.Data;

@Data
public class PrintStationPairRequest {
    private String pairingCode;
    private String machineName;
    private String serviceVersion;
    private Object capabilities;
}
