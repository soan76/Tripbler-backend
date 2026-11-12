package com.tripbler.backend.common.ratelimit;

import java.util.Map;

import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 인증 없이 열린 인증·가입 경로의 요청 횟수를 IP별로 제한한다.
 *
 * IP는 접속 주소(getRemoteAddr)만 쓴다. 프록시가 없는 단일 서버 전제라
 * X-Forwarded-For는 믿지 않는다.
 * 두 send-code 경로는 이메일 한도와 함께 판정해야 하므로 여기서 세지 않고 서비스가 처리한다.
 * 거절되면 TooManyRequestsException을 던지고 GlobalExceptionHandler가 429로 응답한다.
 */
public class AuthRateLimitInterceptor implements HandlerInterceptor {

    private static final Map<String, RateLimitBucket> BUCKETS = Map.of(
        "POST /api/v1/auth/login", RateLimitBucket.LOGIN_IP,
        "POST /api/v1/auth/refresh", RateLimitBucket.SESSION_IP,
        "POST /api/v1/auth/logout", RateLimitBucket.SESSION_IP,
        "POST /api/v1/auth/find-id/verify-code", RateLimitBucket.RECOVERY_SIGNUP_IP,
        "POST /api/v1/auth/password-reset/verify-code", RateLimitBucket.RECOVERY_SIGNUP_IP,
        "POST /api/v1/auth/password-reset", RateLimitBucket.RECOVERY_SIGNUP_IP,
        "POST /api/v1/users", RateLimitBucket.RECOVERY_SIGNUP_IP,
        "GET /api/v1/users/check-login-id", RateLimitBucket.RECOVERY_SIGNUP_IP
    );

    private final AuthRateLimiter limiter;

    public AuthRateLimitInterceptor(AuthRateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    public boolean preHandle(
        HttpServletRequest request,
        HttpServletResponse response,
        Object handler
    ) {
        // 원문 URI가 아니라 Spring이 매칭한 경로 패턴으로 판단한다.
        // 원문 URI로 비교하면 /api/v1/auth/%6Cogin 처럼 인코딩한 경로가 같은 핸들러에 닿으면서도 세지지 않는다.
        Object pattern = request.getAttribute(
            HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE
        );

        if (pattern == null) {
            return true;
        }

        // Spring MVC는 @GetMapping 핸들러로 HEAD도 처리하므로 HEAD는 GET 한도로 센다.
        String method = request.getMethod();
        if ("HEAD".equals(method)) {
            method = "GET";
        }

        RateLimitBucket bucket = BUCKETS.get(method + " " + pattern);

        if (bucket != null) {
            limiter.acquire(bucket, request.getRemoteAddr());
        }

        return true;
    }
}
