package com.restaurant.pos.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.pos.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Enterprise-grade Authentication Entry Point.
 * 
 * Ensures that ALL unauthenticated requests to protected endpoints
 * return a consistent 401 Unauthorized JSON response (never a 403).
 * This is critical for the frontend token-refresh interceptor,
 * which listens for 401 to trigger a silent refresh.
 */
@Component
@RequiredArgsConstructor
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        System.out.println("===> [AUTH] EntryPoint: 401 for " + request.getMethod() + " " + request.getServletPath());

        String origin = request.getHeader("Origin");
        if (origin != null && !origin.isBlank()) {
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Access-Control-Allow-Credentials", "true");
            response.setHeader("Vary", "Origin");
        }
        
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiResponse<Object> apiResponse = ApiResponse.error("Authentication required. Please log in.");
        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
    }
}
