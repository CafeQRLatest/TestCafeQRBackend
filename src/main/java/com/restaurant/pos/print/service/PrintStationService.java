package com.restaurant.pos.print.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.pos.client.domain.Terminal;
import com.restaurant.pos.client.repository.TerminalRepository;
import com.restaurant.pos.common.exception.BusinessException;
import com.restaurant.pos.common.tenant.TenantContext;
import com.restaurant.pos.print.domain.PrintJob;
import com.restaurant.pos.print.domain.PrintJobAttempt;
import com.restaurant.pos.print.domain.PrintJobStatus;
import com.restaurant.pos.print.domain.PrintStation;
import com.restaurant.pos.print.dto.PrintJobStatusRequest;
import com.restaurant.pos.print.dto.PrintStationEnrollmentRequest;
import com.restaurant.pos.print.dto.PrintStationConfigurationRequest;
import com.restaurant.pos.print.dto.PrintStationHeartbeatRequest;
import com.restaurant.pos.print.dto.PrintStationPairRequest;
import com.restaurant.pos.print.repository.PrintJobAttemptRepository;
import com.restaurant.pos.print.repository.PrintJobRepository;
import com.restaurant.pos.print.repository.PrintStationRepository;
import com.restaurant.pos.print.exception.PrintStationAuthenticationException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PrintStationService {

    private static final int MAX_CLAIM = 20;
    private static final int LEASE_SECONDS = 60;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PrintStationRepository stationRepository;
    private final PrintJobRepository jobRepository;
    private final PrintJobAttemptRepository attemptRepository;
    private final TerminalRepository terminalRepository;
    private final PrintConfigurationService configurationService;
    private final ObjectMapper objectMapper;

    @Transactional
    public Map<String, Object> createEnrollment(PrintStationEnrollmentRequest request) {
        UUID clientId = TenantContext.getCurrentTenant();
        if (clientId == null || request.getTerminalId() == null) {
            throw new BusinessException("Terminal is required");
        }
        Terminal terminal = terminalRepository.findByIdAndClientId(request.getTerminalId(), clientId)
                .orElseThrow(() -> new BusinessException("Terminal not found"));

        if (request.isFallbackForBranch()) {
            stationRepository.findAllByClientIdAndOrgIdAndFallbackForBranchTrueAndIsactive(
                    clientId, terminal.getOrgId(), "Y"
            ).forEach(existing -> {
                existing.setFallbackForBranch(false);
                stationRepository.save(existing);
            });
        }

        String pairingCode = randomPairingCode();
        PrintStation station = stationRepository.findByClientIdAndTerminalId(clientId, terminal.getId())
                .orElseGet(PrintStation::new);
        station.setClientId(clientId);
        station.setOrgId(terminal.getOrgId());
        station.setTerminalId(terminal.getId());
        station.setName(nonBlank(request.getName(), terminal.getName() + " Print Station"));
        station.setPairingCodeHash(hash(pairingCode));
        station.setPairingExpiresAt(LocalDateTime.now().plusMinutes(10));
        station.setStatus("PAIRING");
        station.setFallbackForBranch(request.isFallbackForBranch());
        station.setIsactive("Y");
        stationRepository.save(station);

        Map<String, Object> response = stationDto(station);
        response.put("pairingCode", pairingCode);
        response.put("expiresAt", station.getPairingExpiresAt());
        return response;
    }

    @Transactional
    public Map<String, Object> pair(PrintStationPairRequest request) {
        if (request == null || request.getPairingCode() == null || request.getPairingCode().isBlank()) {
            throw new BusinessException("Pairing code is required");
        }
        PrintStation station = stationRepository.findByPairingCodeHashAndIsactive(
                        hash(normalizePairingCode(request.getPairingCode())), "Y")
                .filter(value -> value.getPairingExpiresAt() != null
                        && value.getPairingExpiresAt().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new BusinessException("Pairing code is invalid or expired"));

        String token = randomToken();
        station.setStationTokenHash(hash(token));
        station.setPairingCodeHash(null);
        station.setPairingExpiresAt(null);
        station.setPairedAt(LocalDateTime.now());
        station.setLastHeartbeatAt(LocalDateTime.now());
        station.setServiceVersion(request.getServiceVersion());
        station.setCapabilitiesJson(writeJson(request.getCapabilities()));
        station.setStatus("ONLINE");
        stationRepository.save(station);

        Map<String, Object> response = stationDto(station);
        response.put("stationToken", token);
        response.put("configuration", configurationService.effectiveForStation(
                station.getClientId(), station.getOrgId(), station.getTerminalId()));
        return response;
    }

    @Transactional
    public Map<String, Object> heartbeat(String rawToken, PrintStationHeartbeatRequest request) {
        PrintStation station = authenticate(rawToken);
        station.setLastHeartbeatAt(LocalDateTime.now());
        station.setStatus(nonBlank(request == null ? null : request.getServiceStatus(), "ONLINE"));
        if (request != null) {
            station.setServiceVersion(request.getServiceVersion());
            station.setCapabilitiesJson(writeJson(request.getCapabilities()));
        }
        stationRepository.save(station);

        Map<String, Object> response = stationDto(station);
        response.put("serverTime", LocalDateTime.now());
        response.put("configuration", configurationService.effectiveForStation(
                station.getClientId(), station.getOrgId(), station.getTerminalId()));
        return response;
    }

    @Transactional
    public Map<String, Object> syncConfiguration(
            String rawToken,
            PrintStationConfigurationRequest request
    ) {
        PrintStation station = authenticate(rawToken);
        Map<String, Object> configuration = configurationService.syncForStation(station, request);
        station.setLastHeartbeatAt(LocalDateTime.now());
        station.setStatus("ONLINE");
        stationRepository.save(station);
        return configuration;
    }

    @Transactional
    public List<PrintJob> claim(String rawToken, int requestedLimit) {
        PrintStation station = authenticate(rawToken);
        int limit = Math.max(1, Math.min(requestedLimit <= 0 ? 5 : requestedLimit, MAX_CLAIM));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime offlineTimeout = now.minusSeconds(90);
        List<PrintJob> jobs = jobRepository.findStationClaimable(
                station.getClientId(),
                station.getOrgId(),
                station.getTerminalId(),
                Boolean.TRUE.equals(station.getFallbackForBranch()),
                List.of(PrintJobStatus.PENDING, PrintJobStatus.RETRY, PrintJobStatus.RETRY_WAIT),
                PrintJobStatus.LEASED,
                now,
                offlineTimeout,
                PageRequest.of(0, limit)
        );
        for (PrintJob job : jobs) {
            job.setStatus(PrintJobStatus.LEASED);
            job.setLeasedByStationId(station.getId());
            job.setLeaseToken(UUID.randomUUID().toString());
            job.setLeaseExpiresAt(now.plusSeconds(LEASE_SECONDS));
            job.setClaimedAt(now);
            job.setClaimedByTerminalId(station.getTerminalId());
            job.setAttempts((job.getAttempts() == null ? 0 : job.getAttempts()) + 1);
            job.setErrorMessage(null);
            job.setFailureCode(null);
        }
        station.setLastHeartbeatAt(now);
        station.setStatus("ONLINE");
        stationRepository.save(station);
        List<PrintJob> saved = jobRepository.saveAll(jobs);
        saved.forEach(job -> recordAttempt(job, station, PrintJobStatus.LEASED, "Job leased to station"));
        return saved;
    }

    @Transactional
    public PrintJob updateJob(String rawToken, UUID jobId, PrintJobStatusRequest request) {
        PrintStation station = authenticate(rawToken);
        PrintJob job = jobRepository.findById(jobId)
                .filter(value -> station.getClientId().equals(value.getClientId()))
                .filter(value -> station.getId().equals(value.getLeasedByStationId()))
                .orElseThrow(() -> new BusinessException("Print job is not leased by this station"));
        if (request == null || request.getLeaseToken() == null) {
            throw new BusinessException("Print job lease token is required");
        }

        PrintJobStatus status;
        try {
            status = PrintJobStatus.valueOf(nonBlank(request.getStatus(), "FAILED").toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Unsupported print job status");
        }
        if (!List.of(
                PrintJobStatus.LOCAL_QUEUED,
                PrintJobStatus.SPOOLING,
                PrintJobStatus.SPOOLED,
                PrintJobStatus.COMPLETED,
                PrintJobStatus.PRINTED,
                PrintJobStatus.RETRY_WAIT,
                PrintJobStatus.HELD_AMBIGUOUS,
                PrintJobStatus.FAILED
        ).contains(status)) {
            throw new BusinessException("Station cannot set this print job status");
        }

        LocalDateTime now = LocalDateTime.now();
        job.setStatus(status);
        job.setSpoolJobId(request.getSpoolJobId());
        job.setPrinterProfileId(request.getPrinterProfileId());
        job.setRouteId(request.getRouteId());
        job.setErrorMessage(request.getMessage());
        job.setFailureCode(request.getFailureCode());
        job.setAmbiguous(Boolean.TRUE.equals(request.getAmbiguous())
                || status == PrintJobStatus.HELD_AMBIGUOUS);

        if (status == PrintJobStatus.LOCAL_QUEUED) {
            job.setLocalQueuedAt(now);
            job.setLeaseExpiresAt(now.plusMinutes(10));
        } else if (status == PrintJobStatus.SPOOLING) {
            job.setLeaseExpiresAt(now.plusMinutes(5));
        } else if (status == PrintJobStatus.SPOOLED
                || status == PrintJobStatus.COMPLETED
                || status == PrintJobStatus.PRINTED) {
            job.setPrintedAt(now);
            clearLease(job);
        } else if (status == PrintJobStatus.RETRY_WAIT) {
            job.setNextAttemptAt(now.plusSeconds(retryDelaySeconds(job.getAttempts())));
            clearLease(job);
        } else {
            clearLease(job);
        }
        PrintJob saved = jobRepository.save(job);
        recordAttempt(saved, station, status, request.getMessage());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listStations() {
        UUID clientId = TenantContext.getCurrentTenant();
        if (clientId == null) {
            return List.of();
        }
        return stationRepository.findAllByClientIdOrderByCreatedAtDesc(clientId).stream()
                .map(this::stationDto)
                .toList();
    }

    @Transactional
    public Map<String, Object> revoke(UUID stationId) {
        UUID clientId = TenantContext.getCurrentTenant();
        PrintStation station = stationRepository.findById(stationId)
                .filter(value -> value.getClientId().equals(clientId))
                .orElseThrow(() -> new BusinessException("Print station not found"));
        station.setStationTokenHash(null);
        station.setStatus("REVOKED");
        station.setIsactive("N");
        stationRepository.save(station);
        return stationDto(station);
    }

    public PrintStation authenticate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new PrintStationAuthenticationException("Print station token is required");
        }
        return stationRepository.findByStationTokenHashAndIsactive(hash(rawToken.trim()), "Y")
                .orElseThrow(() -> new PrintStationAuthenticationException("Print station token is invalid"));
    }

    public Map<String, Object> stationDto(PrintStation station) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", station.getId());
        out.put("clientId", station.getClientId());
        out.put("orgId", station.getOrgId());
        out.put("terminalId", station.getTerminalId());
        out.put("name", station.getName());
        out.put("status", station.getStatus());
        out.put("fallbackForBranch", station.getFallbackForBranch());
        out.put("pairedAt", station.getPairedAt());
        out.put("lastHeartbeatAt", station.getLastHeartbeatAt());
        out.put("serviceVersion", station.getServiceVersion());
        out.put("active", station.isActive());
        return out;
    }

    private void clearLease(PrintJob job) {
        job.setLeaseToken(null);
        job.setLeaseExpiresAt(null);
        job.setLeasedByStationId(null);
    }

    private int retryDelaySeconds(Integer attempts) {
        int count = Math.max(1, attempts == null ? 1 : attempts);
        return Math.min(300, 5 * (1 << Math.min(count - 1, 5)));
    }

    private void recordAttempt(PrintJob job, PrintStation station, PrintJobStatus status, String message) {
        attemptRepository.save(PrintJobAttempt.builder()
                .printJobId(job.getId())
                .stationId(station == null ? null : station.getId())
                .attemptNumber(job.getAttempts() == null ? 0 : job.getAttempts())
                .status(status.name())
                .message(message)
                .failureCode(job.getFailureCode())
                .spoolJobId(job.getSpoolJobId())
                .build());
    }

    private String writeJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private String randomPairingCode() {
        return String.format("%03d-%03d", RANDOM.nextInt(1000), RANDOM.nextInt(1000));
    }

    private String normalizePairingCode(String value) {
        String digits = value.replaceAll("\\D", "");
        return digits.length() == 6 ? digits.substring(0, 3) + "-" + digits.substring(3) : value.trim();
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(64);
            for (byte b : digest) {
                out.append(String.format("%02x", b));
            }
            return out.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
