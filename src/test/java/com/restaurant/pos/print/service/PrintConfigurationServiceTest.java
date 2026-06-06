package com.restaurant.pos.print.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.pos.client.domain.Terminal;
import com.restaurant.pos.client.repository.TerminalRepository;
import com.restaurant.pos.common.tenant.TenantContext;
import com.restaurant.pos.print.domain.PrintConfiguration;
import com.restaurant.pos.print.repository.PrintConfigurationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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
