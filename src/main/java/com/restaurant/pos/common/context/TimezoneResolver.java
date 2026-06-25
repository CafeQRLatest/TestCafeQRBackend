package com.restaurant.pos.common.context;

import com.restaurant.pos.client.domain.Client;
import com.restaurant.pos.client.domain.Organization;
import com.restaurant.pos.client.repository.ClientRepository;
import com.restaurant.pos.client.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class TimezoneResolver {

    private final OrganizationRepository organizationRepository;
    private final ClientRepository clientRepository;
    
    // Lightweight cache to prevent DB flooding during background jobs and reports
    private final ConcurrentHashMap<String, ZoneId> timezoneCache = new ConcurrentHashMap<>();

    /**
     * Resolves the correct Timezone for any transaction.
     * Hierarchy: Organization -> Client -> UTC
     */
    public ZoneId resolveTimezone(UUID clientId, UUID orgId) {
        String cacheKey = (clientId != null ? clientId.toString() : "null") + ":" + (orgId != null ? orgId.toString() : "null");
        
        return timezoneCache.computeIfAbsent(cacheKey, key -> {
            try {
                if (orgId != null) {
                    String orgTz = organizationRepository.findById(orgId)
                            .map(Organization::getTimezone)
                            .orElse(null);
                    if (orgTz != null && !orgTz.isBlank()) {
                        return parseTimezone(orgTz);
                    }
                }
                
                if (clientId != null) {
                    String clientTz = clientRepository.findById(clientId)
                            .map(Client::getTimezone)
                            .orElse(null);
                    if (clientTz != null && !clientTz.isBlank()) {
                        return parseTimezone(clientTz);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to resolve timezone for clientId={} orgId={}", clientId, orgId, e);
            }
            
            return ZoneId.of("UTC");
        });
    }

    public void evictCache(UUID clientId, UUID orgId) {
        if (clientId != null && orgId != null) {
            timezoneCache.remove(clientId + ":" + orgId);
        } else {
            timezoneCache.clear();
        }
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
