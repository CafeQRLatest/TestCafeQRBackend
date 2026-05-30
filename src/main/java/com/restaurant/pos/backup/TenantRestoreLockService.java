package com.restaurant.pos.backup;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TenantRestoreLockService {

    private final Map<UUID, Instant> lockedUntilByClient = new ConcurrentHashMap<>();

    public boolean tryLock(UUID clientId, Duration ttl) {
        cleanup();
        Instant expiresAt = Instant.now().plus(ttl);
        return lockedUntilByClient.putIfAbsent(clientId, expiresAt) == null;
    }

    public void unlock(UUID clientId) {
        lockedUntilByClient.remove(clientId);
    }

    public boolean isLocked(UUID clientId) {
        Instant expiresAt = lockedUntilByClient.get(clientId);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt.isBefore(Instant.now())) {
            lockedUntilByClient.remove(clientId);
            return false;
        }
        return true;
    }

    private void cleanup() {
        Instant now = Instant.now();
        lockedUntilByClient.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
    }
}
