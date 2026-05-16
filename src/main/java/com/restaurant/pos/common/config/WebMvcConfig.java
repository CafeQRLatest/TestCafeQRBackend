package com.restaurant.pos.common.config;

import com.restaurant.pos.common.tenant.TenantInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final TenantInterceptor tenantInterceptor;

    @Value("${app.cors.allowed-origins:http://localhost:3000,https://cafe-test-qr-frontend.vercel.app,https://cafe-qr-frontend.vercel.app}")
    private String[] allowedOrigins;

    @Override
    public void addInterceptors(@org.springframework.lang.NonNull InterceptorRegistry registry) {
        registry.addInterceptor(java.util.Objects.requireNonNull(tenantInterceptor));
    }

    @Override
    public void addCorsMappings(@org.springframework.lang.NonNull org.springframework.web.servlet.config.annotation.CorsRegistry registry) {
        String[] configuredOrigins = java.util.Arrays.stream(allowedOrigins)
                .filter(origin -> origin != null && !origin.isBlank())
                .map(String::trim)
                .toArray(String[]::new);
        registry.addMapping("/**")
                .allowedOriginPatterns(configuredOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
