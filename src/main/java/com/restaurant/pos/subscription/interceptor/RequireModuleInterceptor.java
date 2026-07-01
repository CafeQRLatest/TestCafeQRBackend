package com.restaurant.pos.subscription.interceptor;

import com.restaurant.pos.common.tenant.TenantContext;
import com.restaurant.pos.subscription.annotation.RequireModule;
import com.restaurant.pos.subscription.domain.ClientSubscriptionModule;
import com.restaurant.pos.subscription.domain.ModuleName;
import com.restaurant.pos.subscription.repository.ClientSubscriptionModuleRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RequireModuleInterceptor implements HandlerInterceptor {

    private final ClientSubscriptionModuleRepository clientSubscriptionModuleRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;
        RequireModule requireModule = handlerMethod.getMethodAnnotation(RequireModule.class);
        if (requireModule == null) {
            requireModule = handlerMethod.getBeanType().getAnnotation(RequireModule.class);
        }

        if (requireModule != null) {
            ModuleName requiredModule = requireModule.value();
            UUID clientId = TenantContext.getCurrentTenant();
            UUID orgId = TenantContext.getCurrentOrg();

            if (clientId == null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.setContentType("application/json");
                response.getWriter().write("{\"success\":false,\"message\":\"Client context not resolved.\"}");
                return false;
            }

            // Query active modules
            List<ClientSubscriptionModule> activeModules = clientSubscriptionModuleRepository.findByClientId(clientId);
            boolean hasAccess = false;
            
            for (ClientSubscriptionModule m : activeModules) {
                if (m.getModuleName() == requiredModule && "ACTIVE".equalsIgnoreCase(m.getStatus())) {
                    if (m.getExpiryDate() == null || m.getExpiryDate().isAfter(LocalDateTime.now())) {
                        hasAccess = true;
                        break;
                    }
                }
            }

            if (!hasAccess) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"success\":false,\"message\":\"Payment Required: Premium module " + requiredModule + " is not active.\"}");
                return false;
            }
        }

        return true;
    }
}
