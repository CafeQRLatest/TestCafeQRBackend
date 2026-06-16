package com.restaurant.pos.order.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxReportDetailsDto {
    private List<TaxCodeSummaryRow> taxCodeSummary;
    private List<B2BInvoiceRow> b2bInvoices;
    private List<B2CSummaryRow> b2cSummary;
    private List<MonthlyAggregationRow> monthlyAggregation;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaxCodeSummaryRow {
        private String taxCode;
        private String description;
        private String uqc;
        private BigDecimal totalQuantity;
        private BigDecimal taxableValue;
        private BigDecimal integratedTax;
        private BigDecimal centralTax;
        private BigDecimal stateTax;
        private BigDecimal cessAmount;
        private BigDecimal taxRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class B2BInvoiceRow {
        private String taxId;
        private String receiverName;
        private String invoiceNo;
        private String invoiceDate;
        private BigDecimal invoiceValue;
        private String placeOfSupply;
        private String reverseCharge;
        private String invoiceType;
        private BigDecimal taxRate;
        private BigDecimal taxableValue;
        private BigDecimal igst;
        private BigDecimal cgst;
        private BigDecimal sgst;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class B2CSummaryRow {
        private String type;
        private String placeOfSupply;
        private BigDecimal taxRate;
        private BigDecimal taxableValue;
        private BigDecimal igst;
        private BigDecimal cgst;
        private BigDecimal sgst;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyAggregationRow {
        private String period;
        private BigDecimal taxableValue;
        private BigDecimal igst;
        private BigDecimal cgst;
        private BigDecimal sgst;
    }
}
