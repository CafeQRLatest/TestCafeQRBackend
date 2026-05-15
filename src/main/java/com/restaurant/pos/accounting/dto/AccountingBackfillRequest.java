package com.restaurant.pos.accounting.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

@Data
public class AccountingBackfillRequest {
    private LocalDateTime from;
    private LocalDateTime to;
    private Set<String> sourceTypes;
    private boolean dryRun;
}
