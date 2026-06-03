package com.restaurant.pos.client.controller;

import com.restaurant.pos.client.domain.Organization;
import com.restaurant.pos.client.dto.OrganizationDto;
import com.restaurant.pos.client.service.OrganizationService;
import com.restaurant.pos.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('MANAGER') or hasRole('STAFF')")
    public ResponseEntity<ApiResponse<List<OrganizationDto>>> getMyOrganizations() {
        return ResponseEntity.ok(ApiResponse.success(organizationService.getMyOrganizations().stream()
                .map(OrganizationDto::from)
                .toList()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('MANAGER') or hasRole('STAFF')")
    public ResponseEntity<ApiResponse<OrganizationDto>> getOrganization(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(OrganizationDto.from(organizationService.getOrganizationById(id))));
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrganizationDto>> createOrganization(@RequestBody Organization org) {
        return ResponseEntity.ok(ApiResponse.success(OrganizationDto.from(organizationService.createOrganization(org))));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrganizationDto>> updateOrganization(@PathVariable UUID id, @RequestBody Organization org) {
        return ResponseEntity.ok(ApiResponse.success(OrganizationDto.from(organizationService.updateOrganization(id, org))));
    }

}
