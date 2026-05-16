package com.restaurant.pos.accounting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountingReconciliationDto {
    private LocalDateTime from;
    private LocalDateTime to;
    private long salesOrders;
    private long invoices;
    private long payments;
    private long postedInvoices;
    private long postedPayments;
    private long missingInvoices;
    private long missingPayments;
    private boolean outOfSync;
    @Builder.Default
    private List<String> warnings = new ArrayList<>();
}
