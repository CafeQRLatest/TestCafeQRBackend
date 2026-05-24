package com.restaurant.pos.common.context;

import java.util.UUID;

/**
 * Interface to resolve operational contexts and security attributes.
 * Decouples services from static ThreadLocal state, enabling clean testing
 * and support for async execution contexts or virtual threads.
 */
public interface ContextProvider {
    UUID getCurrentTenant();
    UUID getCurrentOrg();
    UUID getCurrentUserId();
    UUID getCurrentTerminal();
    String getCurrentUserEmail();
    boolean isSuperAdmin();
    boolean hasRole(String role);
    java.time.ZoneId getCurrentTimezone();
}
