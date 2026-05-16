package com.restaurant.pos.accounting.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

@Data
public class AccountingBackfillRequest {
    private String from;
    private String to;
    private Set<String> sourceTypes;
    private boolean dryRun;

    public void setFrom(String from) {
        this.from = from;
    }

    public void setFrom(LocalDateTime from) {
        this.from = from == null ? null : from.toString();
    }

    public void setTo(String to) {
        this.to = to;
    }

    public void setTo(LocalDateTime to) {
        this.to = to == null ? null : to.toString();
    }
}
