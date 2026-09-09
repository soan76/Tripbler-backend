package com.tripbler.backend.common.config;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final Path profileImageRoot;

    public WebConfig(
        @Value("${profile-image.storage.local-root}")
        String localRoot
    ) {
        this.profileImageRoot =
            Paths.get(localRoot)
                .toAbsolutePath()
                .normalize();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry
            .addMapping("/api/**")
            .allowedOriginPatterns(
                "http://localhost:*",
                "http://127.0.0.1:*"
            )
            .allowedMethods(
                "GET",
                "POST",
                "PUT",
                "PATCH",
                "DELETE",
                "OPTIONS"
            )
            .allowedHeaders("*")
            .allowCredentials(false)
            .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(
        ResourceHandlerRegistry registry
    ) {
        String resourceLocation =
            profileImageRoot
                .toUri()
                .toString();

        if (!resourceLocation.endsWith("/")) {
            resourceLocation += "/";
        }

        registry
            .addResourceHandler("/uploads/**")
            .addResourceLocations(resourceLocation);
    }
}