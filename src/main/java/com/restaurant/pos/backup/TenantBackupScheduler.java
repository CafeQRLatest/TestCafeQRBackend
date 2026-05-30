package com.restaurant.pos.backup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Scheduled job that runs every day at 02:00 AM server time.
 *
 * For each tenant with schedule_enabled = true:
 *  - DAILY  → creates a backup every day
 *  - WEEKLY → creates a backup every Monday
 *
 * After creating, it enforces the retention_count setting by
 * deleting the oldest excess backups (both DB rows and files).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantBackupScheduler {

    private final JdbcTemplate jdbcTemplate;
    private final TenantBackupService backupService;

    /**
     * Fires at 02:00 AM daily. Non-overlapping (fixedDelay semantics are not needed
     * because @Scheduled cron will skip if the previous invocation is still running).
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void runScheduledBackups() {
        log.info("[Backup Scheduler] Starting scheduled backup sweep...");

        List<Map<String, Object>> enabledTenants = jdbcTemplate.queryForList("""
                SELECT client_id, schedule_frequency, retention_count
                FROM tenant_backup_settings
                WHERE schedule_enabled = TRUE
                  AND schedule_frequency IN ('DAILY', 'WEEKLY')
                """);

        if (enabledTenants.isEmpty()) {
            log.info("[Backup Scheduler] No tenants have scheduled backups enabled. Done.");
            return;
        }

        int backed = 0;
        int skipped = 0;

        for (Map<String, Object> tenant : enabledTenants) {
            UUID clientId = (UUID) tenant.get("client_id");
            String frequency = (String) tenant.get("schedule_frequency");
            int retention = ((Number) tenant.get("retention_count")).intValue();

            if ("WEEKLY".equalsIgnoreCase(frequency) && LocalDate.now().getDayOfWeek() != DayOfWeek.MONDAY) {
                skipped++;
                continue;
            }

            try {
                log.info("[Backup Scheduler] Creating {} backup for client {}.", frequency, clientId);
                backupService.createScheduledBackup(clientId);
                backed++;
            } catch (Exception ex) {
                log.error("[Backup Scheduler] Failed to create backup for client {}: {}", clientId, ex.getMessage(), ex);
            }

            // Retention cleanup — runs after every scheduled backup
            try {
                backupService.enforceRetention(clientId, retention);
            } catch (Exception ex) {
                log.error("[Backup Scheduler] Retention cleanup failed for client {}: {}", clientId, ex.getMessage(), ex);
            }
        }

        log.info("[Backup Scheduler] Completed. Created: {}, Skipped (not due): {}.", backed, skipped);
    }
}
