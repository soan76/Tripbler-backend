package com.tripbler.backend.common.config;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.tripbler.backend.common.ratelimit.AuthRateLimitInterceptor;
import com.tripbler.backend.common.ratelimit.AuthRateLimiter;

// AuthRateLimiter는 컴포넌트 스캔 대신 여기서만 등록한다.
// 그래서 WebConfig를 불러오는 곳(애플리케이션, @WebMvcTest 슬라이스)에는 항상 제한기와 인터셉터가 함께 붙고,
// 등록 경로가 하나라 같은 타입 빈이 두 개 생기지 않는다.
@Configuration
@Import(AuthRateLimiter.class)
public class WebConfig implements WebMvcConfigurer {

    private final Path profileImageRoot;
    private final AuthRateLimiter authRateLimiter;

    public WebConfig(
        @Value("${profile-image.storage.local-root}")
        String localRoot,
        AuthRateLimiter authRateLimiter
    ) {
        this.authRateLimiter = authRateLimiter;
        this.profileImageRoot =
            Paths.get(localRoot)
                .toAbsolutePath()
                .normalize();
    }

    // 공개 인증·가입 경로의 IP별 요청 제한. 항상 등록한다(제한기를 구할 수 없으면 기동 실패).
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(
            new AuthRateLimitInterceptor(authRateLimiter)
        );
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