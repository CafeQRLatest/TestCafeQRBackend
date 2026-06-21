 package com.restaurant.pos.client.controller;

import com.restaurant.pos.client.domain.Terminal;
import com.restaurant.pos.client.service.TerminalService;
import com.restaurant.pos.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/terminals")
@RequiredArgsConstructor
public class TerminalController {

    private final TerminalService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<Terminal>>> getTerminals() {
        return ResponseEntity.ok(ApiResponse.success(service.getMyTerminals()));
    }

    @GetMapping("/org/{orgId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<Terminal>>> getTerminalsByOrg(@PathVariable UUID orgId) {
        return ResponseEntity.ok(ApiResponse.success(service.getTerminalsByOrg(orgId)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Terminal>> getTerminal(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(service.getTerminalById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Terminal>> createTerminal(@RequestBody Terminal terminal) {
        return ResponseEntity.ok(ApiResponse.success(service.saveTerminal(terminal)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Terminal>> updateTerminal(@PathVariable UUID id, @RequestBody Terminal terminal) {
        terminal.setId(id);
        return ResponseEntity.ok(ApiResponse.success(service.saveTerminal(terminal)));
    }

}
