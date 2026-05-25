package com.restaurant.pos.common.context;

import com.restaurant.pos.common.tenant.TenantContext;
import com.restaurant.pos.common.util.SecurityUtils;
import com.restaurant.pos.client.repository.ClientRepository;
import com.restaurant.pos.client.domain.Client;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.time.ZoneId;
import java.util.UUID;

/**
 * Request-scoped ContextProvider implementation.
 * Caches the resolved ZoneId per request to avoid redundant database reads.
 */
@Slf4j
@Component
@RequestScope
@RequiredArgsConstructor
public class RequestContextProvider implements ContextProvider {

    private final ClientRepository clientRepository;

    // Lazy-cached per request — database hit happens at most once
    private ZoneId resolvedZoneId;

    @Override
    public UUID getCurrentTenant() {
        return TenantContext.getCurrentTenant();
    }

    @Override
    public UUID getCurrentOrg() {
        return TenantContext.getCurrentOrg();
    }

    @Override
    public UUID getCurrentTerminal() {
        return TenantContext.getCurrentTerminal();
    }

    @Override
    public UUID getCurrentUserId() {
        return SecurityUtils.getCurrentUserId();
    }

    @Override
    public String getCurrentUserEmail() {
        return SecurityUtils.getCurrentUserEmail();
    }

    @Override
    public boolean isSuperAdmin() {
        return SecurityUtils.isSuperAdmin();
    }

    @Override
    public boolean hasRole(String role) {
        return SecurityUtils.hasRole(role);
    }

    @Override
    public ZoneId getCurrentTimezone() {
        if (resolvedZoneId != null) return resolvedZoneId;
        try {
            UUID clientId = getCurrentTenant();
            if (clientId != null) {
                resolvedZoneId = clientRepository.findById(clientId)
                        .map(Client::getTimezone)
                        .map(this::parseTimezone)
                        .orElse(ZoneId.of("UTC"));
                return resolvedZoneId;
            }
        } catch (Exception e) {
            log.warn("Failed to resolve timezone for tenant={}", getCurrentTenant(), e);
        }
        return resolvedZoneId = ZoneId.of("UTC");
    }

    private ZoneId parseTimezone(String tz) {
        try {
            if (tz.startsWith("UTC")) {
                String offset = tz.substring(3).trim();
                if (offset.isEmpty()) return ZoneId.of("UTC");
                if (!offset.startsWith("+") && !offset.startsWith("-")) offset = "+" + offset;
                return ZoneId.of(offset);
            }
            return ZoneId.of(tz);
        } catch (Exception e) {
            log.warn("Invalid timezone '{}', falling back to UTC", tz, e);
            return ZoneId.of("UTC");
        }
    }
}
