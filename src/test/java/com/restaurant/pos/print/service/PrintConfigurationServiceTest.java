package com.restaurant.pos.print.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.pos.client.domain.Terminal;
import com.restaurant.pos.client.repository.TerminalRepository;
import com.restaurant.pos.common.exception.BusinessException;
import com.restaurant.pos.common.tenant.TenantContext;
import com.restaurant.pos.print.domain.PrintConfiguration;
import com.restaurant.pos.print.dto.PrintConfigurationRequest;
import com.restaurant.pos.print.repository.PrintConfigurationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PrintConfigurationServiceTest {

    private final UUID clientId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();
    private final UUID terminalId = UUID.randomUUID();

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void effectiveConfigurationMergesClientBranchAndTerminalInOrder() {
        PrintConfigurationRepository repository = mock(PrintConfigurationRepository.class);
        TerminalRepository terminalRepository = mock(TerminalRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        PrintConfigurationService service = new PrintConfigurationService(
                repository, terminalRepository, objectMapper);

        TenantContext.setCurrentTenant(clientId);
        TenantContext.setCurrentOrg(orgId);
        Terminal terminal = Terminal.builder().id(terminalId).name("POS 1").build();
        terminal.setClientId(clientId);
        terminal.setOrgId(orgId);
        when(terminalRepository.findByIdAndClientId(terminalId, clientId)).thenReturn(Optional.of(terminal));

        when(repository.findByClientIdAndScopeTypeAndScopeIdIsNull(clientId, PrintConfigurationService.CLIENT))
                .thenReturn(Optional.of(layer(PrintConfigurationService.CLIENT, null,
                        """
                        {"defaults":{"billOutput":"THERMAL","kotOutput":"THERMAL"},"profiles":[{"id":"client"}]}
                        """)));
        when(repository.findByClientIdAndScopeTypeAndScopeId(clientId, PrintConfigurationService.ORGANIZATION, orgId))
                .thenReturn(Optional.of(layer(PrintConfigurationService.ORGANIZATION, orgId,
                        """
                        {"defaults":{"billOutput":"BOTH"},"profiles":[{"id":"branch"}]}
                        """)));
        when(repository.findByClientIdAndScopeTypeAndScopeId(clientId, PrintConfigurationService.TERMINAL, terminalId))
                .thenReturn(Optional.of(layer(PrintConfigurationService.TERMINAL, terminalId,
                        """
                        {"defaults":{"kotOutput":"REGULAR"}}
                        """)));

        Map<String, Object> result = service.effective(terminalId, null);

        @SuppressWarnings("unchecked")
        Map<String, Object> defaults = (Map<String, Object>) result.get("defaults");
        assertThat(defaults)
                .containsEntry("billOutput", "BOTH")
                .containsEntry("kotOutput", "REGULAR");
        assertThat(result.get("profiles").toString()).contains("branch").doesNotContain("client");
    }

    @Test
    void savesIndependentKotBillAndInvoicePrinterAssignments() {
        PrintConfigurationRepository repository = mock(PrintConfigurationRepository.class);
        TerminalRepository terminalRepository = mock(TerminalRepository.class);
        PrintConfigurationService service = new PrintConfigurationService(
                repository, terminalRepository, new ObjectMapper());
        TenantContext.setCurrentTenant(clientId);

        PrintConfigurationRequest request = request(Map.of(
                "profiles", java.util.List.of(
                        profile("kitchen", "Kitchen", java.util.List.of("KOT")),
                        profile("receipt", "Receipt", java.util.List.of("BILL")),
                        profile("invoice", "Invoice", java.util.List.of("INVOICE"))
                ),
                "routes", java.util.List.of(),
                "defaults", Map.of(
                        "kotProfileIds", java.util.List.of("kitchen"),
                        "billProfileIds", java.util.List.of("receipt"),
                        "invoiceProfileIds", java.util.List.of("invoice"),
                        "kotMode", "MIRROR",
                        "billMode", "MIRROR",
                        "invoiceMode", "MIRROR"
                )
        ));

        service.save(request);

        verify(repository).save(org.mockito.ArgumentMatchers.argThat(entity ->
                entity.getSettingsJson().contains("\"invoiceProfileIds\":[\"invoice\"]")
                        && entity.getSettingsJson().contains("\"kotProfileIds\":[\"kitchen\"]")
                        && entity.getSettingsJson().contains("\"billProfileIds\":[\"receipt\"]")));
    }

    @Test
    void rejectsDefaultAssignmentToUnknownProfile() {
        PrintConfigurationService service = serviceWithTenant();
        PrintConfigurationRequest request = request(Map.of(
                "profiles", java.util.List.of(profile("kitchen", "Kitchen", java.util.List.of("KOT"))),
                "defaults", Map.of("kotProfileIds", java.util.List.of("missing"))
        ));

        assertThatThrownBy(() -> service.save(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("KOT default printer references an unknown profile");
    }

    @Test
    void rejectsDefaultAssignmentToIncompatibleProfile() {
        PrintConfigurationService service = serviceWithTenant();
        PrintConfigurationRequest request = request(Map.of(
                "profiles", java.util.List.of(profile("receipt", "Receipt", java.util.List.of("BILL"))),
                "defaults", Map.of("kotProfileIds", java.util.List.of("receipt"))
        ));

        assertThatThrownBy(() -> service.save(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Receipt does not support KOT");
    }

    private PrintConfigurationService serviceWithTenant() {
        TenantContext.setCurrentTenant(clientId);
        return new PrintConfigurationService(
                mock(PrintConfigurationRepository.class),
                mock(TerminalRepository.class),
                new ObjectMapper());
    }

    private PrintConfigurationRequest request(Map<String, Object> settings) {
        PrintConfigurationRequest request = new PrintConfigurationRequest();
        request.setScopeType(PrintConfigurationService.CLIENT);
        request.setSettings(settings);
        return request;
    }

    private Map<String, Object> profile(String id, String name, java.util.List<String> documents) {
        return Map.of(
                "id", id,
                "name", name,
                "connectionType", "WINDOWS_QUEUE",
                "windowsPrinterName", "POS58 Printer",
                "enabled", true,
                "documents", documents
        );
    }

    private PrintConfiguration layer(String scope, UUID scopeId, String settings) {
        PrintConfiguration layer = PrintConfiguration.builder()
                .scopeType(scope)
                .scopeId(scopeId)
                .settingsJson(settings)
                .revision(1)
                .build();
        layer.setClientId(clientId);
        layer.setOrgId(orgId);
        return layer;
    }
}
