package com.restaurant.pos.client.dto;

import com.restaurant.pos.client.domain.Terminal;

import java.util.UUID;

public record TerminalDto(
        UUID id,
        UUID clientId,
        UUID orgId,
        String name,
        String terminalCode,
        String deviceType,
        String ipAddress,
        String isactive,
        UUID deviceId,
        String offlineBillingMode,
        Boolean offlineBillingEnabled,
        Boolean printStationEnabled,
        OrganizationRef organization
) {

    public static TerminalDto from(Terminal terminal) {
        if (terminal == null) {
            return null;
        }

        return new TerminalDto(
                terminal.getId(),
                terminal.getClientId(),
                terminal.getOrgId(),
                terminal.getName(),
                terminal.getTerminalCode(),
                terminal.getDeviceType(),
                terminal.getIpAddress(),
                terminal.getIsactive(),
                terminal.getDeviceId(),
                terminal.getOfflineBillingMode(),
                terminal.getOfflineBillingEnabled(),
                terminal.getPrintStationEnabled(),
                null
        );
    }

    public record OrganizationRef(UUID id, String name) {
    }
}
