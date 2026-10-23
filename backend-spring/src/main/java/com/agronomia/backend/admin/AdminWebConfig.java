package com.agronomia.backend.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * Registra el interceptor de sesión y el CORS para el frontend React.
 */
@Configuration
public class AdminWebConfig implements WebMvcConfigurer {

    private final AdminAuthInterceptor authInterceptor;
    private final String[] allowedOrigins;

    public AdminWebConfig(
            AdminAuthInterceptor authInterceptor,
            @Value("${admin.cors.origins:http://localhost:5173,http://127.0.0.1:5173}")
            String allowedOrigins
    ) {
        this.authInterceptor = authInterceptor;
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/admin/**")
                .excludePathPatterns("/api/admin/auth/login", "/api/admin/health");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/admin/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PATCH", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type")
                .maxAge(3600);
    }
}
