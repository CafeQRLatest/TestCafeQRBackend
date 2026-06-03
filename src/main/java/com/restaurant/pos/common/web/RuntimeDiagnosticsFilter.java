package com.restaurant.pos.common.web;

import com.restaurant.pos.common.tenant.TenantContext;
import com.restaurant.pos.common.util.SecurityUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
public class RuntimeDiagnosticsFilter extends OncePerRequestFilter {

    private static final long SLOW_ENDPOINT_MS = 1500L;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!isRuntimeEndpoint(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        long started = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException ex) {
            log.error(
                    "Runtime endpoint failed | method={} path={} status={} durationMs={} user={} clientId={} orgId={} terminalId={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    elapsedMs(started),
                    currentUserEmail(),
                    TenantContext.getCurrentTenant(),
                    TenantContext.getCurrentOrg(),
                    TenantContext.getCurrentTerminal(),
                    ex
            );
            throw ex;
        } finally {
            long durationMs = elapsedMs(started);
            int status = response.getStatus();
            if (status >= 400 || durationMs >= SLOW_ENDPOINT_MS) {
                log.info(
                        "Runtime endpoint observed | method={} path={} status={} durationMs={} user={} clientId={} orgId={} terminalId={}",
                        request.getMethod(),
                        request.getRequestURI(),
                        status,
                        durationMs,
                        currentUserEmail(),
                        TenantContext.getCurrentTenant(),
                        TenantContext.getCurrentOrg(),
                        TenantContext.getCurrentTerminal()
                );
            }
        }
    }

    private boolean isRuntimeEndpoint(String path) {
        return path != null
                && (path.startsWith("/api/v1/accounting/")
                || path.startsWith("/api/v1/reports")
                || path.startsWith("/api/v1/configurations")
                || path.startsWith("/api/v1/organizations")
                || path.startsWith("/api/v1/terminals"));
    }

    private String currentUserEmail() {
        try {
            String email = SecurityUtils.getCurrentUserEmail();
            return email == null || email.isBlank() ? "anonymous" : email;
        } catch (RuntimeException ignored) {
            return "unknown";
        }
    }

    private long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }
}
