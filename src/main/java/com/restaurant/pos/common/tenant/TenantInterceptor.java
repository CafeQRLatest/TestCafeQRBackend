package com.restaurant.pos.common.tenant;

import com.restaurant.pos.common.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

@Slf4j
@Component
public class TenantInterceptor implements HandlerInterceptor {

    private static final String CLIENT_HEADER = "X-Client-ID";
    private static final String ORG_HEADER = "X-Org-ID";
    private static final String TERMINAL_HEADER = "X-Terminal-ID";
    private static final String USER_ID_HEADER = "X-User-ID";
    private static final String EMAIL_HEADER = "X-User-Email";
    private static final String ROLE_HEADER = "X-User-Role";


    @Override
    public boolean preHandle(@org.springframework.lang.NonNull HttpServletRequest request, @org.springframework.lang.NonNull HttpServletResponse response, @org.springframework.lang.NonNull Object handler) {
        String clientIdStr = request.getHeader(CLIENT_HEADER);
        String orgIdStr = request.getHeader(ORG_HEADER);
        String terminalIdStr = request.getHeader(TERMINAL_HEADER);
        String userIdStr = request.getHeader(USER_ID_HEADER);
        
        UUID clientId = parseUuid(clientIdStr);
        UUID orgId = parseUuid(orgIdStr);
        UUID terminalId = parseUuid(terminalIdStr);
        UUID userId = parseUuid(userIdStr);
        boolean clearOrgContext = isGlobalOrgSelection(orgIdStr) && SecurityUtils.isSuperAdmin();

        // ONLY set if header is provided. This prevents wiping data set by JWT filter
        if (clientId != null) TenantContext.setCurrentTenant(clientId);
        if (orgId != null) TenantContext.setCurrentOrg(orgId);
        else if (clearOrgContext) TenantContext.setCurrentOrg(null);
        if (terminalId != null) TenantContext.setCurrentTerminal(terminalId);

        // Update UserContext only for provided headers
        UserContext.UserContextData context = UserContext.getContext();
        if (userId != null) context.setUserId(userId);
        if (clientId != null) context.setClientId(clientId);
        if (orgId != null) context.setOrgId(orgId);
        else if (clearOrgContext) context.setOrgId(null);
        if (terminalId != null) context.setTerminalId(terminalId);
        
        String email = request.getHeader(EMAIL_HEADER);
        if (email != null) context.setEmail(email);
        
        String role = request.getHeader(ROLE_HEADER);
        if (role != null) context.setRole(role);
        
        return true;
    }

    private boolean isGlobalOrgSelection(String orgIdStr) {
        return orgIdStr != null && "0".equals(orgIdStr.trim());
    }

    private UUID parseUuid(String uuidStr) {
        if (uuidStr == null || uuidStr.isEmpty() || "0".equals(uuidStr)) {
            return null;
        }
        try {
            return UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID header: {}", uuidStr);
            return null;
        }
    }

    @Override
    public void afterCompletion(@org.springframework.lang.NonNull HttpServletRequest request, @org.springframework.lang.NonNull HttpServletResponse response, @org.springframework.lang.NonNull Object handler, @org.springframework.lang.Nullable Exception ex) {
        TenantContext.clear();
        UserContext.clear();
    }
}
