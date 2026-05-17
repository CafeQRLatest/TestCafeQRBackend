package com.restaurant.pos.common.controller;

import com.restaurant.pos.common.dto.ApiResponse;
import com.restaurant.pos.common.dto.ConfigurationDto;
import com.restaurant.pos.common.service.SystemConfigurationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/configurations")
@RequiredArgsConstructor
public class ConfigurationController {

    private final SystemConfigurationService configurationService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ConfigurationDto>> getConfiguration() {
        return ResponseEntity.ok(ApiResponse.success(configurationService.getConfiguration()));
    }

    @PutMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ConfigurationDto>> updateConfiguration(@RequestBody ConfigurationDto dto) {
        return ResponseEntity.ok(ApiResponse.success(configurationService.updateConfiguration(dto)));
    }
}
