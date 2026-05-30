package com.restaurant.pos.backup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.pos.common.dto.ApiResponse;
import com.restaurant.pos.common.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TenantRestoreLockInterceptor implements HandlerInterceptor {

    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final TenantRestoreLockService restoreLockService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!MUTATING_METHODS.contains(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI();
        if (!path.startsWith("/api/v1/") || isExempt(path)) {
            return true;
        }

        UUID clientId = TenantContext.getCurrentTenant();
        if (clientId == null || !restoreLockService.isLocked(clientId)) {
            return true;
        }

        response.setStatus(423);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResponse.error("A data restore is in progress for this restaurant. Please try again after it finishes.")
        ));
        return false;
    }

    private boolean isExempt(String path) {
        return path.startsWith("/api/v1/backups")
                || path.startsWith("/api/v1/backup-settings")
                || path.startsWith("/api/v1/auth")
                || path.startsWith("/api/v1/public")
                || path.startsWith("/api/v1/debug");
    }
}
