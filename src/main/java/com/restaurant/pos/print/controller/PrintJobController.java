package com.restaurant.pos.print.controller;

import com.restaurant.pos.common.dto.ApiResponse;
import com.restaurant.pos.print.domain.PrintJob;
import com.restaurant.pos.print.dto.CreatePrintJobRequest;
import com.restaurant.pos.print.dto.PrintJobStatusRequest;
import com.restaurant.pos.print.service.PrintJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/print-jobs")
@RequiredArgsConstructor
public class PrintJobController {

    private final PrintJobService printJobService;

    @GetMapping("/recent")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> recent() {
        try {
            return ResponseEntity.ok(ApiResponse.success(printJobService.recent().stream()
                    .map(this::toDto)
                    .toList()));
        } catch (Exception ex) {
            return ResponseEntity.ok(ApiResponse.success(List.of()));
        }
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> enqueue(@RequestBody CreatePrintJobRequest request) {
        PrintJob job = printJobService.enqueueOrderJob(request.getOrderId(), request.getJobKind());
        return ResponseEntity.ok(ApiResponse.success(toDto(job)));
    }

    @PostMapping("/claim")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> claim(@RequestParam(defaultValue = "5") int limit) {
        try {
            return ResponseEntity.ok(ApiResponse.success(printJobService.claimJobs(limit).stream()
                    .map(this::toDto)
                    .toList()));
        } catch (Exception ex) {
            return ResponseEntity.ok(ApiResponse.success(List.of()));
        }
    }

    @PostMapping("/{id}/printed")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> printed(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(toDto(printJobService.markPrinted(id))));
    }

    @PostMapping("/orders/{orderId}/{jobKind}/printed")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> printedForOrder(@PathVariable UUID orderId, @PathVariable String jobKind) {
        return ResponseEntity.ok(ApiResponse.success(printJobService.markPrintedForOrder(orderId, jobKind).stream()
                .map(this::toDto)
                .toList()));
    }

    @PostMapping("/{id}/failed")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> failed(@PathVariable UUID id, @RequestBody(required = false) PrintJobStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success(toDto(printJobService.markFailed(id, request == null ? null : request.getMessage()))));
    }

    @PostMapping("/{id}/retry")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> retry(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(toDto(printJobService.retry(id))));
    }

    private Map<String, Object> toDto(PrintJob job) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", job.getId());
        dto.put("orderId", job.getOrderId());
        dto.put("offlineOperationId", job.getOfflineOperationId());
        dto.put("sourceOperationId", job.getSourceOperationId());
        dto.put("sourceTerminalId", job.getSourceTerminalId());
        dto.put("claimedByTerminalId", job.getClaimedByTerminalId());
        dto.put("jobKind", job.getJobKind() == null ? "bill" : job.getJobKind().name().toLowerCase());
        dto.put("status", job.getStatus() == null ? "PENDING" : job.getStatus().name());
        dto.put("attempts", job.getAttempts() == null ? 0 : job.getAttempts());
        dto.put("errorMessage", job.getErrorMessage());
        dto.put("payload", printJobService.payload(job));
        dto.put("createdAt", job.getCreatedAt());
        dto.put("updatedAt", job.getUpdatedAt());
        return dto;
    }
}
