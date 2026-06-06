package com.restaurant.pos.print.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.pos.client.repository.TerminalRepository;
import com.restaurant.pos.print.domain.PrintJob;
import com.restaurant.pos.print.domain.PrintJobKind;
import com.restaurant.pos.print.domain.PrintJobStatus;
import com.restaurant.pos.print.domain.PrintStation;
import com.restaurant.pos.print.repository.PrintJobAttemptRepository;
import com.restaurant.pos.print.repository.PrintJobRepository;
import com.restaurant.pos.print.repository.PrintStationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PrintStationServiceTest {

    @Test
    void claimCreatesExpiringStationLeaseAndReclaimsStaleJobs() throws Exception {
        PrintStationRepository stationRepository = mock(PrintStationRepository.class);
        PrintJobRepository jobRepository = mock(PrintJobRepository.class);
        PrintJobAttemptRepository attemptRepository = mock(PrintJobAttemptRepository.class);
        TerminalRepository terminalRepository = mock(TerminalRepository.class);
        PrintConfigurationService configurationService = mock(PrintConfigurationService.class);
        PrintStationService service = new PrintStationService(
                stationRepository,
                jobRepository,
                attemptRepository,
                terminalRepository,
                configurationService,
                new ObjectMapper()
        );

        String token = "station-secret";
        UUID clientId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID terminalId = UUID.randomUUID();
        UUID stationId = UUID.randomUUID();
        PrintStation station = PrintStation.builder()
                .id(stationId)
                .terminalId(terminalId)
                .name("POS Print Station")
                .stationTokenHash(sha256(token))
                .fallbackForBranch(true)
                .isactive("Y")
                .build();
        station.setClientId(clientId);
        station.setOrgId(orgId);
        when(stationRepository.findByStationTokenHashAndIsactive(sha256(token), "Y"))
                .thenReturn(Optional.of(station));

        PrintJob stale = PrintJob.builder()
                .id(UUID.randomUUID())
                .jobKind(PrintJobKind.KOT)
                .status(PrintJobStatus.LEASED)
                .dedupeKey("order:kot")
                .payloadJson("{}")
                .attempts(1)
                .build();
        stale.setClientId(clientId);
        stale.setOrgId(orgId);
        when(jobRepository.findStationClaimable(
                eq(clientId),
                eq(orgId),
                eq(terminalId),
                eq(true),
                anyCollection(),
                eq(PrintJobStatus.LEASED),
                any(),
                any(Pageable.class)
        )).thenReturn(List.of(stale));
        when(jobRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<PrintJob> claimed = service.claim(token, 5);

        assertThat(claimed).hasSize(1);
        assertThat(stale.getStatus()).isEqualTo(PrintJobStatus.LEASED);
        assertThat(stale.getLeasedByStationId()).isEqualTo(stationId);
        assertThat(stale.getClaimedByTerminalId()).isEqualTo(terminalId);
        assertThat(stale.getLeaseToken()).isNotBlank();
        assertThat(stale.getLeaseExpiresAt()).isNotNull();
        assertThat(stale.getAttempts()).isEqualTo(2);
        verify(stationRepository).save(station);
        verify(attemptRepository).save(argThat(attempt ->
                attempt.getPrintJobId().equals(stale.getId())
                        && attempt.getStatus().equals("LEASED")
                        && attempt.getAttemptNumber() == 2
        ));
    }

    private static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder output = new StringBuilder();
        for (byte item : digest) output.append(String.format("%02x", item));
        return output.toString();
    }
}
