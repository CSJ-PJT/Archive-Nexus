package com.archivenexus.backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Configuration
public class ArchiveWebSecurityConfig implements WebMvcConfigurer {
    private final String[] allowedOrigins;

    public ArchiveWebSecurityConfig(@Value("${archive.security.allowed-origins:http://127.0.0.1:15173,http://localhost:15173}") String origins) {
        this.allowedOrigins = Arrays.stream(origins.split(",")).map(String::trim).filter(value -> !value.isBlank()).toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", ArchiveSecurityPolicy.SOURCE_HEADER, ArchiveSecurityPolicy.SCOPE_HEADER)
                .exposedHeaders("Content-Type")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
