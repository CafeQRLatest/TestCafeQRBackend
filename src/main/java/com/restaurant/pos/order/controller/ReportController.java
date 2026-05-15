package com.restaurant.pos.order.controller;

import com.restaurant.pos.common.dto.ApiResponse;
import com.restaurant.pos.invoice.domain.Invoice;
import com.restaurant.pos.order.dto.report.*;
import com.restaurant.pos.order.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private static final Duration MAX_REPORT_RANGE = Duration.ofDays(31);

    private final ReportService reportService;

    @GetMapping("/sales-summary")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<SalesSummaryDto>> getSalesSummary(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        validateReportRange(from, to);
        return ResponseEntity.ok(ApiResponse.success(reportService.getSalesSummary(from, to)));
    }

    @GetMapping("/sales-orders")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<OrderReportDto>>> getSalesOrders(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        validateReportRange(from, to);
        return ResponseEntity.ok(ApiResponse.success(reportService.getSalesOrders(from, to)));
    }

    @GetMapping("/sales-invoices")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<SalesInvoiceReportDto>>> getSalesInvoices(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "ALL") String type) {
        validateReportRange(from, to);
        return ResponseEntity.ok(ApiResponse.success(reportService.getSalesInvoices(from, to, type)));
    }

    @GetMapping("/item-wise")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<ItemSalesDto>>> getItemWiseSales(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        validateReportRange(from, to);
        return ResponseEntity.ok(ApiResponse.success(reportService.getItemWiseSales(from, to)));
    }

    @GetMapping("/payment-breakdown")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<PaymentBreakdownDto>>> getPaymentBreakdown(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        validateReportRange(from, to);
        return ResponseEntity.ok(ApiResponse.success(reportService.getPaymentBreakdown(from, to)));
    }

    @GetMapping("/tax-summary")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<TaxSummaryDto>>> getTaxSummary(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        validateReportRange(from, to);
        return ResponseEntity.ok(ApiResponse.success(reportService.getTaxSummary(from, to)));
    }

    @GetMapping("/hourly")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<HourlySalesDto>>> getHourlySales(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        validateReportRange(from, to);
        return ResponseEntity.ok(ApiResponse.success(reportService.getHourlySales(from, to)));
    }

    @GetMapping("/invoices")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<InvoiceReportDto>>> getInvoices(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "ALL") String type) {
        validateReportRange(from, to);
        return ResponseEntity.ok(ApiResponse.success(reportService.getInvoices(from, to, type)));
    }

    @GetMapping("/profit-loss")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<ProfitLossDto>> getProfitLoss(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        validateReportRange(from, to);
        return ResponseEntity.ok(ApiResponse.success(reportService.getProfitLoss(from, to)));
    }

    @PostMapping("/invoices/{id}/void")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<Invoice>> voidInvoice(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        return ResponseEntity.ok(ApiResponse.success(reportService.voidInvoice(id, reason)));
    }

    private void validateReportRange(Instant from, Instant to) {
        if (from == null || to == null) {
            return;
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("Report from date must be before to date");
        }
        if (Duration.between(from, to).compareTo(MAX_REPORT_RANGE) > 0) {
            throw new IllegalArgumentException("Report date range cannot exceed 31 days");
        }
    }
}
