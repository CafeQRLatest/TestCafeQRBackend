package com.restaurant.pos.accounting.dto;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountingBackfillResponse {
    private boolean dryRun;
    private int scanned;
    private int posted;
    private int skipped;
    private int reversed;
    private int failed;

    @Builder.Default
    private List<String> failures = new ArrayList<>();
}
