package com.restaurant.pos.common.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.pos.common.dto.ConfigurationDto;
import com.restaurant.pos.common.entity.SystemConfiguration;
import com.restaurant.pos.common.exception.BusinessException;
import com.restaurant.pos.common.repository.SystemConfigurationRepository;
import com.restaurant.pos.common.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemConfigurationServiceTest {

    private SystemConfigurationRepository repository;
    private SystemConfigurationService service;
    private UUID clientA;
    private UUID clientB;

    @BeforeEach
    void setUp() {
        repository = mock(SystemConfigurationRepository.class);
        service = new SystemConfigurationService(repository, new ObjectMapper());
        clientA = UUID.randomUUID();
        clientB = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void getConfigurationCreatesTenantConfigForCurrentClient() {
        TenantContext.setCurrentTenant(clientA);
        when(repository.findFirstByClientIdAndOrgIdIsNull(clientA)).thenReturn(Optional.empty());
        when(repository.save(any(SystemConfiguration.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ConfigurationDto configuration = service.getConfiguration();

        assertThat(configuration.isQrOrderingEnabled()).isTrue();
        assertThat(configuration.isSendToKitchenEnabled()).isTrue();

        ArgumentCaptor<SystemConfiguration> configCaptor = ArgumentCaptor.forClass(SystemConfiguration.class);
        verify(repository).save(configCaptor.capture());
        SystemConfiguration saved = configCaptor.getValue();
        assertThat(saved.getClientId()).isEqualTo(clientA);
        assertThat(saved.getOrgId()).isNull();
    }

    @Test
    void updateConfigurationOnlyUpdatesCurrentClient() {
        TenantContext.setCurrentTenant(clientA);
        SystemConfiguration configA = defaultConfig(clientA);
        when(repository.findFirstByClientIdAndOrgIdIsNull(clientA)).thenReturn(Optional.of(configA));
        when(repository.save(any(SystemConfiguration.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ConfigurationDto clientAUpdate = ConfigurationDto.builder()
                .billFooter("Client A footer")
                .taxLabelGlobal("GST-A")
                .onlinePaymentEnabled(true)
                .build();

        service.updateConfiguration(clientAUpdate);

        TenantContext.setCurrentTenant(clientB);
        SystemConfiguration configB = defaultConfig(clientB);
        when(repository.findFirstByClientIdAndOrgIdIsNull(clientB)).thenReturn(Optional.of(configB));

        ConfigurationDto clientBUpdate = ConfigurationDto.builder()
                .billFooter("Client B footer")
                .taxLabelGlobal("GST-B")
                .onlinePaymentEnabled(false)
                .build();

        service.updateConfiguration(clientBUpdate);

        assertThat(configA.getBillFooter()).isEqualTo("Client A footer");
        assertThat(configA.getTaxLabelGlobal()).isEqualTo("GST-A");
        assertThat(configA.isOnlinePaymentEnabled()).isTrue();
        assertThat(configB.getBillFooter()).isEqualTo("Client B footer");
        assertThat(configB.getTaxLabelGlobal()).isEqualTo("GST-B");
        assertThat(configB.isOnlinePaymentEnabled()).isFalse();
    }

    @Test
    void sameClientDifferentOrgUsesSameTenantConfiguration() {
        TenantContext.setCurrentTenant(clientA);
        TenantContext.setCurrentOrg(UUID.randomUUID());
        SystemConfiguration configA = defaultConfig(clientA);
        when(repository.findFirstByClientIdAndOrgIdIsNull(clientA)).thenReturn(Optional.of(configA));

        service.getConfiguration();

        TenantContext.setCurrentOrg(UUID.randomUUID());
        service.getConfiguration();

        verify(repository, times(2)).findFirstByClientIdAndOrgIdIsNull(clientA);
    }

    @Test
    void updateConfigurationWithoutTenantFailsSafely() {
        TenantContext.clear();

        assertThatThrownBy(() -> service.updateConfiguration(ConfigurationDto.builder().build()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Client context is required to update system configuration");

        verify(repository, never()).save(any(SystemConfiguration.class));
    }

    @Test
    void getConfigurationWithoutTenantFallsBackToGlobalRow() {
        TenantContext.clear();
        SystemConfiguration globalConfig = defaultConfig(null);
        globalConfig.setOnlinePaymentEnabled(true);
        when(repository.findFirstByClientIdIsNullAndOrgIdIsNullOrderByCreatedAtAsc())
                .thenReturn(Optional.of(globalConfig));

        ConfigurationDto configuration = service.getConfiguration();

        assertThat(configuration.isOnlinePaymentEnabled()).isTrue();
        verify(repository, never()).findFirstByClientIdAndOrgIdIsNull(any());
    }

    @Test
    void publicClientLookupUsesProvidedClientWithoutTenantContext() {
        TenantContext.clear();
        SystemConfiguration publicConfig = defaultConfig(clientA);
        publicConfig.setOnlinePaymentEnabled(true);
        when(repository.findFirstByClientIdAndOrgIdIsNull(clientA)).thenReturn(Optional.of(publicConfig));

        ConfigurationDto configuration = service.getConfigurationForClient(clientA);

        assertThat(configuration.isOnlinePaymentEnabled()).isTrue();
        verify(repository).findFirstByClientIdAndOrgIdIsNull(clientA);
    }

    private SystemConfiguration defaultConfig(UUID clientId) {
        return SystemConfiguration.builder()
                .id(UUID.randomUUID())
                .clientId(clientId)
                .orgId(null)
                .qrOrderingEnabled(true)
                .sendToKitchenEnabled(true)
                .taxLabelGlobal("GST")
                .taxRatesJson("[]")
                .currencySymbol("₹")
                .currencyPosition("before")
                .build();
    }
}
