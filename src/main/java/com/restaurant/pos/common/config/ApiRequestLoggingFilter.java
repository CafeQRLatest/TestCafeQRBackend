package com.restaurant.pos.common.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
public class ApiRequestLoggingFilter extends OncePerRequestFilter {

    private static final List<String> HIGH_RISK_PREFIXES = List.of(
            "/api/v1/orders/sales/live",
            "/api/v1/orders/history",
            "/api/v1/sync/bootstrap",
            "/api/v1/sync/changes",
            "/api/v1/reports"
    );

    @Value("${app.observability.log-all-api-requests:false}")
    private boolean logAllApiRequests;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long startedAt = System.nanoTime();
        ContentCachingResponseWrapper wrapped = new ContentCachingResponseWrapper(response);

        try {
            filterChain.doFilter(request, wrapped);
        } finally {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            int responseBytes = wrapped.getContentSize();
            String method = request.getMethod();
            String path = request.getRequestURI();
            String query = request.getQueryString();
            String route = query == null || query.isBlank() ? path : path + "?" + query;
            int status = wrapped.getStatus();

            String message = "api_request method={} route={} status={} durationMs={} responseBytes={}";
            if (logAllApiRequests || isHighRisk(path)) {
                log.info(message, method, route, status, durationMs, responseBytes);
            } else {
                log.debug(message, method, route, status, durationMs, responseBytes);
            }

            wrapped.copyBodyToResponse();
        }
    }

    private boolean isHighRisk(String path) {
        return HIGH_RISK_PREFIXES.stream().anyMatch(path::startsWith);
    }
}
