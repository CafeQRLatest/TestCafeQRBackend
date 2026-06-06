package com.restaurant.pos.print.controller;

import com.restaurant.pos.common.dto.ApiResponse;
import com.restaurant.pos.print.dto.PrintStationEnrollmentRequest;
import com.restaurant.pos.print.service.PrintStationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/print-stations")
@RequiredArgsConstructor
public class PrintStationController {

    private final PrintStationService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list() {
        return ResponseEntity.ok(ApiResponse.success(service.listStations()));
    }

    @PostMapping("/enrollment")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> enrollment(
            @RequestBody PrintStationEnrollmentRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.createEnrollment(request)));
    }

    @DeleteMapping("/{stationId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> revoke(@PathVariable UUID stationId) {
        return ResponseEntity.ok(ApiResponse.success(service.revoke(stationId)));
    }
}
