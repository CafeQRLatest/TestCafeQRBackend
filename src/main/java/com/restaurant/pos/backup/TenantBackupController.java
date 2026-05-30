package com.restaurant.pos.backup;

import com.restaurant.pos.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TenantBackupController {

    private final TenantBackupService backupService;

    @GetMapping("/backup-settings")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<BackupSettingsResponse>> getSettings() {
        return ResponseEntity.ok(ApiResponse.success(backupService.getSettings()));
    }

    @PutMapping("/backup-settings")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<BackupSettingsResponse>> updateSettings(@RequestBody BackupSettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Backup settings saved.", backupService.updateSettings(request)));
    }

    @PostMapping("/backups")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<BackupCreateResponse>> createBackup() {
        return ResponseEntity.ok(ApiResponse.success("Backup generated.", backupService.createManualBackup()));
    }

    @GetMapping("/backups")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<BackupSummaryResponse>>> listBackups() {
        return ResponseEntity.ok(ApiResponse.success(backupService.listBackups()));
    }

    @GetMapping("/backups/{id}/download")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Resource> downloadBackup(@PathVariable UUID id) throws IOException {
        BackupDownload download = backupService.getDownload(id);
        InputStreamResource resource = new InputStreamResource(Files.newInputStream(download.path()));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + download.fileName() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(download.sizeBytes())
                .body(resource);
    }

    @PostMapping(value = "/backups/restore/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<BackupPreviewResponse>> previewRestore(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success("Restore preview prepared.", backupService.previewRestore(file)));
    }

    @PostMapping("/backups/restore/confirm")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<RestoreConfirmResponse>> confirmRestore(@RequestBody RestoreConfirmRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Backup restored.", backupService.confirmRestore(request)));
    }
}

record BackupSettingsRequest(Boolean scheduleEnabled, String scheduleFrequency, Integer retentionCount) {
}

record BackupSettingsResponse(boolean scheduleEnabled, String scheduleFrequency, int retentionCount, Instant updatedAt) {
}

record BackupSummaryResponse(
        UUID id,
        String type,
        String status,
        String fileName,
        long sizeBytes,
        String checksumSha256,
        long rowCount,
        Instant createdAt,
        Instant expiresAt,
        String errorMessage
) {
}

record BackupCreateResponse(UUID id, String status, String fileName, long sizeBytes, String checksumSha256, BackupManifest manifest) {
}

record BackupPreviewResponse(String restoreToken, Instant expiresAt, BackupManifest manifest, List<String> warnings) {
}

record RestoreConfirmRequest(String restoreToken, String currentPassword, String otp, String confirmationText) {
}

record RestoreConfirmResponse(UUID preRestoreBackupId, int restoredTables, long restoredRows, BackupManifest manifest) {
}

record BackupManifest(
        String formatVersion,
        String appName,
        String createdAt,
        String clientId,
        String clientName,
        String createdByEmail,
        String schemaVersion,
        List<String> tableOrder,
        List<BackupTableSummary> tables,
        Map<String, Map<String, String>> totals,
        long totalRows,
        String warning
) {
}

record BackupTableSummary(String table, int rows, String checksumSha256) {
}

record BackupDownload(String fileName, Path path, long sizeBytes) {
}
