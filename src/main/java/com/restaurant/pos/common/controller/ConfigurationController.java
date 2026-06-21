package com.restaurant.pos.common.controller;

import com.restaurant.pos.common.dto.ApiResponse;
import com.restaurant.pos.common.dto.ConfigurationDto;
import com.restaurant.pos.common.service.SystemConfigurationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

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
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<ConfigurationDto>> updateConfiguration(@RequestBody ConfigurationDto dto) {
        return ResponseEntity.ok(ApiResponse.success(configurationService.updateConfiguration(dto)));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // BRANCH-LEVEL CONFIG OVERRIDES
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Returns the branch-specific override row (or client default if none).
     * Accessible by SUPER_ADMIN and ADMIN to allow branch managers to view their billing settings.
     */
    @GetMapping("/branch/{orgId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<ConfigurationDto>> getBranchConfiguration(@PathVariable UUID orgId) {
        return ResponseEntity.ok(ApiResponse.success(configurationService.getConfigurationForBranch(orgId)));
    }

    /**
     * Returns the effective (runtime) configuration the branch actually uses,
     * including fallback to client default when no branch override exists.
     */
    @GetMapping("/branch/{orgId}/effective")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<ConfigurationDto>> getEffectiveBranchConfiguration(@PathVariable UUID orgId) {
        return ResponseEntity.ok(ApiResponse.success(configurationService.getEffectiveConfigurationForBranch(orgId)));
    }

    @PutMapping("/branch/{orgId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ConfigurationDto>> updateBranchConfiguration(
            @PathVariable UUID orgId,
            @RequestBody ConfigurationDto dto) {
        return ResponseEntity.ok(ApiResponse.success(configurationService.updateBranchConfiguration(orgId, dto)));
    }

    @DeleteMapping("/branch/{orgId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<String>> deleteBranchConfiguration(@PathVariable UUID orgId) {
        configurationService.deleteBranchConfiguration(orgId);
        return ResponseEntity.ok(ApiResponse.success("Branch configuration override removed. Branch will now use the default configuration."));
    }
}
