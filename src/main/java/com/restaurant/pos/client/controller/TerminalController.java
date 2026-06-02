package com.restaurant.pos.client.controller;

import com.restaurant.pos.client.domain.Terminal;
import com.restaurant.pos.client.dto.TerminalDto;
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
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<TerminalDto>>> getTerminals() {
        return ResponseEntity.ok(ApiResponse.success(service.getMyTerminals().stream()
                .map(TerminalDto::from)
                .toList()));
    }

    @GetMapping("/org/{orgId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<TerminalDto>>> getTerminalsByOrg(@PathVariable UUID orgId) {
        return ResponseEntity.ok(ApiResponse.success(service.getTerminalsByOrg(orgId).stream()
                .map(TerminalDto::from)
                .toList()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<TerminalDto>> getTerminal(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(TerminalDto.from(service.getTerminalById(id))));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<TerminalDto>> createTerminal(@RequestBody Terminal terminal) {
        return ResponseEntity.ok(ApiResponse.success(TerminalDto.from(service.saveTerminal(terminal))));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<TerminalDto>> updateTerminal(@PathVariable UUID id, @RequestBody Terminal terminal) {
        terminal.setId(id);
        return ResponseEntity.ok(ApiResponse.success(TerminalDto.from(service.saveTerminal(terminal))));
    }

}
