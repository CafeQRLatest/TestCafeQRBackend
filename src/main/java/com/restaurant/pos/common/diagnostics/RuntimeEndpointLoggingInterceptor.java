package com.restaurant.pos.common.diagnostics;

import com.restaurant.pos.common.tenant.TenantContext;
import com.restaurant.pos.common.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.stream.Collectors;

@Slf4j
@Component
public class RuntimeEndpointLoggingInterceptor implements HandlerInterceptor {

    private static final String START_NANOS_ATTRIBUTE = "cafeqr.runtimeEndpoint.startNanos";
    private static final long SLOW_ENDPOINT_THRESHOLD_MS = 1500;

    public static boolean isRuntimeEndpoint(String path) {
        if (path == null) {
            return false;
        }
        return path.equals("/api/v1/terminals")
                || path.startsWith("/api/v1/terminals/")
                || path.equals("/api/v1/configurations")
                || path.startsWith("/api/v1/configurations/")
                || path.equals("/api/v1/organizations")
                || path.startsWith("/api/v1/organizations/")
                || path.equals("/api/v1/accounting")
                || path.startsWith("/api/v1/accounting/");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (isRuntimeEndpoint(request.getRequestURI())) {
            request.setAttribute(START_NANOS_ATTRIBUTE, System.nanoTime());
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        String path = request.getRequestURI();
        if (!isRuntimeEndpoint(path)) {
            return;
        }

        long durationMs = durationMs(request.getAttribute(START_NANOS_ATTRIBUTE));
        int status = response.getStatus();
        String context = buildContext(request, status, durationMs);

        if (ex != null) {
            log.error("Runtime endpoint failed {}", context, ex);
        } else if (status >= 500) {
            log.error("Runtime endpoint failed {}", context);
        } else if (status >= 400) {
            log.warn("Runtime endpoint returned client/security error {}", context);
        } else if (durationMs >= SLOW_ENDPOINT_THRESHOLD_MS) {
            log.info("Runtime endpoint slow {}", context);
        }
    }

    private long durationMs(Object startNanos) {
        if (startNanos instanceof Long start) {
            return (System.nanoTime() - start) / 1_000_000L;
        }
        return -1L;
    }

    private String buildContext(HttpServletRequest request, int status, long durationMs) {
        return "method=%s path=%s query=%s status=%d durationMs=%d user=%s clientId=%s orgId=%s role=%s authorities=%s"
                .formatted(
                        request.getMethod(),
                        request.getRequestURI(),
                        safeValue(request.getQueryString()),
                        status,
                        durationMs,
                        safeValue(SecurityUtils.getCurrentUserEmail()),
                        safeValue(TenantContext.getCurrentTenant()),
                        safeValue(TenantContext.getCurrentOrg()),
                        safeValue(request.getHeader("X-User-Role")),
                        safeValue(authorities())
                );
    }

    private String authorities() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return "anonymous";
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));
    }

    private String safeValue(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }
}
