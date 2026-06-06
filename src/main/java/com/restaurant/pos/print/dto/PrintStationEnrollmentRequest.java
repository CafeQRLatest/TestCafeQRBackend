package com.restaurant.pos.print.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class PrintStationEnrollmentRequest {
    private UUID terminalId;
    private String name;
    private boolean fallbackForBranch;
}
