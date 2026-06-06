package com.restaurant.pos.print.dto;

import lombok.Data;

@Data
public class PrintJobStatusRequest {
    private String message;
    private String status;
    private String leaseToken;
    private String spoolJobId;
    private String printerProfileId;
    private String routeId;
    private String failureCode;
    private Boolean ambiguous;
}
