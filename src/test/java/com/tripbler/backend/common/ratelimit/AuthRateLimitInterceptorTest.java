package com.tripbler.backend.common.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.tripbler.backend.auth.service.AccountDeletionService;
import com.tripbler.backend.auth.service.FindIdVerificationService;
import com.tripbler.backend.auth.service.PasswordResetService;
import com.tripbler.backend.auth.service.PasswordResetVerificationService;
import com.tripbler.backend.common.config.SecurityConfig;
import com.tripbler.backend.common.security.CustomAccessDeniedHandler;
import com.tripbler.backend.common.security.CustomAuthenticationEntryPoint;
import com.tripbler.backend.user.controller.AuthController;
import com.tripbler.backend.user.controller.UserController;
import com.tripbler.backend.user.service.AuthService;
import com.tripbler.backend.user.service.ProfileImageService;
import com.tripbler.backend.user.service.UserService;

@WebMvcTest({AuthController.class, UserController.class})
@Import({
    SecurityConfig.class,
    CustomAuthenticationEntryPoint.class,
    CustomAccessDeniedHandler.class
})
class AuthRateLimitInterceptorTest {

    // WebConfig가 등록하는 제한기를 고정 시계 제한기로 바꿔 끼운다(같은 타입 빈은 하나만 남는다).
    @TestBean private AuthRateLimiter authRateLimiter;

    static AuthRateLimiter authRateLimiter() {
        return new AuthRateLimiter(
            Clock.fixed(Instant.parse("2026-11-12T00:00:00Z"), ZoneOffset.UTC),
            10_000
        );
    }

    @Autowired private MockMvc mockMvc;

    @MockitoBean private AuthService authService;
    @MockitoBean private FindIdVerificationService findIdVerificationService;
    @MockitoBean private PasswordResetVerificationService passwordResetVerificationService;
    @MockitoBean private PasswordResetService passwordResetService;
    @MockitoBean private UserService userService;
    @MockitoBean private ProfileImageService profileImageService;
    @MockitoBean private AccountDeletionService accountDeletionService;
    @MockitoBean private JwtDecoder jwtDecoder;

    private static MockHttpServletRequestBuilder login(String ip) {
        return post("/api/v1/auth/login")
            .with(request -> {
                request.setRemoteAddr(ip);
                return request;
            })
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"loginId": "testuser", "password": "password1!"}
                """);
    }

    private static MockHttpServletRequestBuilder refresh(String ip) {
        return post("/api/v1/auth/refresh")
            .with(request -> {
                request.setRemoteAddr(ip);
                return request;
            })
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"refreshToken": "token"}
                """);
    }

    @Test
    void loginIsLimitedPerIp() throws Exception {
        String ip = "10.1.0.1";
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(login(ip)).andExpect(status().isOk());
        }

        mockMvc.perform(login(ip))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string("Retry-After", "60"))
            .andExpect(jsonPath("$.status").value(429))
            .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
            .andExpect(jsonPath("$.message").value("요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."))
            .andExpect(jsonPath("$.path").value("/api/v1/auth/login"))
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void refreshIsCountedSeparatelyFromLogin() throws Exception {
        String ip = "10.1.0.2";
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(login(ip)).andExpect(status().isOk());
        }
        mockMvc.perform(login(ip)).andExpect(status().isTooManyRequests());

        mockMvc.perform(refresh(ip)).andExpect(status().isOk());
    }

    @Test
    void checkLoginIdIsInRecoverySignupBucket() throws Exception {
        for (int i = 0; i < 30; i++) {
            mockMvc.perform(checkLoginId("10.1.0.3")).andExpect(status().isOk());
        }

        mockMvc.perform(checkLoginId("10.1.0.3"))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string("Retry-After", "60"));
    }

    @Test
    void differentIpIsNotAffected() throws Exception {
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(login("10.1.0.4")).andExpect(status().isOk());
        }
        mockMvc.perform(login("10.1.0.4")).andExpect(status().isTooManyRequests());

        mockMvc.perform(login("10.1.0.5")).andExpect(status().isOk());
    }

    @Test
    void optionsRequestIsNotCounted() throws Exception {
        String ip = "10.1.0.6";
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(
                options("/api/v1/auth/login")
                    .with(request -> {
                        request.setRemoteAddr(ip);
                        return request;
                    })
                    .header("Origin", "http://localhost:3000")
                    .header("Access-Control-Request-Method", "POST")
            );
        }

        mockMvc.perform(login(ip)).andExpect(status().isOk());
    }

    @Test
    void encodedPathIsCountedAsTheSameEndpoint() throws Exception {
        String ip = "10.1.0.7";
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(
                post(URI.create("/api/v1/auth/%6Cogin"))
                    .with(request -> {
                        request.setRemoteAddr(ip);
                        return request;
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"loginId": "testuser", "password": "password1!"}
                        """)
            );
        }

        mockMvc.perform(login(ip)).andExpect(status().isTooManyRequests());
    }

    @Test
    void headRequestIsCountedWithGetLimit() throws Exception {
        String ip = "10.1.0.8";
        for (int i = 0; i < 30; i++) {
            mockMvc.perform(
                head("/api/v1/users/check-login-id")
                    .param("loginId", "testuser")
                    .with(request -> {
                        request.setRemoteAddr(ip);
                        return request;
                    })
            ).andExpect(status().isOk());
        }

        mockMvc.perform(checkLoginId(ip))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string("Retry-After", "60"));
    }

    @Test
    void usesTheReplacedFixedClockLimiter(
        @Autowired ApplicationContext context
    ) {
        assertEquals(
            1, context.getBeanNamesForType(AuthRateLimiter.class).length
        );
        assertSame(
            authRateLimiter, context.getBean(AuthRateLimiter.class)
        );
    }

    private static MockHttpServletRequestBuilder checkLoginId(String ip) {
        return get("/api/v1/users/check-login-id")
            .param("loginId", "testuser")
            .with(request -> {
                request.setRemoteAddr(ip);
                return request;
            });
    }
}
