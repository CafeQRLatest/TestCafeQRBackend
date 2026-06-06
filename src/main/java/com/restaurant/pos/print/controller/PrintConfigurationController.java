package com.restaurant.pos.print.controller;

import com.restaurant.pos.common.dto.ApiResponse;
import com.restaurant.pos.print.dto.PrintConfigurationRequest;
import com.restaurant.pos.print.service.PrintConfigurationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/print-configurations")
@RequiredArgsConstructor
public class PrintConfigurationController {

    private final PrintConfigurationService service;

    @GetMapping("/effective")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> effective(
            @RequestParam(required = false) UUID terminalId,
            @RequestParam(required = false) UUID orgId
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.effective(terminalId, orgId)));
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> save(
            @RequestBody PrintConfigurationRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.save(request)));
    }
}
