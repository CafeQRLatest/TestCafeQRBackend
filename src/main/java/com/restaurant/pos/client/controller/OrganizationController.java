package com.restaurant.pos.client.controller;

import com.restaurant.pos.client.domain.Organization;
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
    public ResponseEntity<ApiResponse<List<Organization>>> getMyOrganizations() {
        return ResponseEntity.ok(ApiResponse.success(organizationService.getMyOrganizations()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('MANAGER') or hasRole('STAFF')")
    public ResponseEntity<ApiResponse<Organization>> getOrganization(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(organizationService.getOrganizationById(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Organization>> createOrganization(@RequestBody Organization org) {
        return ResponseEntity.ok(ApiResponse.success(organizationService.createOrganization(org)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Organization>> updateOrganization(@PathVariable UUID id, @RequestBody Organization org) {
        return ResponseEntity.ok(ApiResponse.success(organizationService.updateOrganization(id, org)));
    }

}
