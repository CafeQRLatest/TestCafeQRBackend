package com.restaurant.pos.sequence.controller;

import com.restaurant.pos.common.dto.ApiResponse;
import com.restaurant.pos.sequence.domain.OfflineSequenceLease;
import com.restaurant.pos.sequence.dto.ReserveOfflineLeaseRequest;
import com.restaurant.pos.sequence.service.OfflineSequenceLeaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/offline-sequence-leases")
@RequiredArgsConstructor
public class OfflineSequenceLeaseController {

    private final OfflineSequenceLeaseService leaseService;

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<OfflineSequenceLease>>> active(@RequestParam(required = false) UUID terminalId) {
        return ResponseEntity.ok(ApiResponse.success(leaseService.active(terminalId)));
    }

    @PostMapping("/reserve-defaults")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<OfflineSequenceLease>>> reserveDefaults(@RequestBody(required = false) ReserveOfflineLeaseRequest request) {
        UUID terminalId = request == null ? null : request.getTerminalId();
        Integer blockSize = request == null ? null : request.getBlockSize();
        return ResponseEntity.ok(ApiResponse.success(leaseService.reserveDefaults(terminalId, blockSize)));
    }
}
