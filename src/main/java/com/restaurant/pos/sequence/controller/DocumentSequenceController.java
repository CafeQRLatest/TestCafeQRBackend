package com.restaurant.pos.sequence.controller;

import com.restaurant.pos.common.dto.ApiResponse;
import com.restaurant.pos.sequence.domain.DocumentSequence;
import com.restaurant.pos.sequence.service.DocumentSequenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/settings/sequences")
@RequiredArgsConstructor
public class DocumentSequenceController {

    private final DocumentSequenceService sequenceService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<DocumentSequence>>> getAllSequences() {
        return ResponseEntity.ok(ApiResponse.success(sequenceService.getAllSequences()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<DocumentSequence>> createSequence(@RequestBody DocumentSequence sequence) {
        return ResponseEntity.ok(ApiResponse.success(sequenceService.createSequence(sequence)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<DocumentSequence>> updateSequence(
            @PathVariable UUID id, 
            @RequestBody DocumentSequence sequence) {
        return ResponseEntity.ok(ApiResponse.success(sequenceService.updateSequence(id, sequence)));
    }
}
