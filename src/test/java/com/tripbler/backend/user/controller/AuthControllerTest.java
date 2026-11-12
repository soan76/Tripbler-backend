package com.tripbler.backend.user.controller;

import com.tripbler.backend.auth.service.FindIdVerificationService;
import com.tripbler.backend.auth.service.PasswordResetVerificationService;
import com.tripbler.backend.auth.service.PasswordResetService;
import com.tripbler.backend.common.config.SecurityConfig;
import com.tripbler.backend.common.exception.BusinessException;
import com.tripbler.backend.common.exception.ErrorCode;
import com.tripbler.backend.common.exception.TooManyRequestsException;
import com.tripbler.backend.common.security.CustomAccessDeniedHandler;
import com.tripbler.backend.common.security.CustomAuthenticationEntryPoint;
import com.tripbler.backend.user.service.AuthService;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({
    SecurityConfig.class,
    CustomAuthenticationEntryPoint.class,
    CustomAccessDeniedHandler.class
})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private FindIdVerificationService findIdVerificationService;

    @MockitoBean
    private PasswordResetVerificationService passwordResetVerificationService;

    @MockitoBean
    private PasswordResetService passwordResetService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void sendCodeRateLimitedReturns429WithRetryAfter()
        throws Exception {

        doThrow(new TooManyRequestsException(42))
            .when(findIdVerificationService)
            .sendVerificationCode("test@gmail.com", "127.0.0.1");

        mockMvc.perform(
            post("/api/v1/auth/find-id/send-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "test@gmail.com"
                    }
                    """)
        )
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string("Retry-After", "42"))
            .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
            .andExpect(jsonPath("$.path").value("/api/v1/auth/find-id/send-code"));
    }

    @Test
    void sendFindIdVerificationCodeSucceedsWithoutAuthentication()
        throws Exception {

        mockMvc.perform(
            post("/api/v1/auth/find-id/send-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "test@gmail.com"
                    }
                    """)
        )
            .andExpect(status().isNoContent());

        verify(findIdVerificationService)
            .sendVerificationCode("test@gmail.com", "127.0.0.1");
    }

    @Test
    void sendFindIdVerificationCodeFailsWithInvalidEmail()
        throws Exception {

        mockMvc.perform(
            post("/api/v1/auth/find-id/send-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "invalid-email"
                    }
                    """)
        )
            .andExpect(status().isBadRequest());

        verifyNoInteractions(findIdVerificationService);
    }

    @Test
    void sendFindIdVerificationCodeFailsWithBlankEmail()
        throws Exception {

        mockMvc.perform(
            post("/api/v1/auth/find-id/send-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": ""
                    }
                    """)
        )
            .andExpect(status().isBadRequest());

        verifyNoInteractions(findIdVerificationService);
    }

    @Test
    void verifyFindIdCodeSucceedsWithoutAuthentication()
        throws Exception {

        when(
            findIdVerificationService.verifyCode(
                "test@gmail.com",
                "123456"
            )
        ).thenReturn("testuser");

        mockMvc.perform(
            post("/api/v1/auth/find-id/verify-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                    "email": "test@gmail.com",
                    "code": "123456"
                    }
                    """)
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.loginId").value("testuser"));

        verify(findIdVerificationService)
            .verifyCode(
                "test@gmail.com",
                "123456"
            );
    }

    @Test
    void verifyFindIdCodeFailsWithInvalidCodeFormat()
        throws Exception {

        mockMvc.perform(
            post("/api/v1/auth/find-id/verify-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                    "email": "test@gmail.com",
                    "code": "123"
                    }
                    """)
        )
            .andExpect(status().isBadRequest());

        verifyNoInteractions(findIdVerificationService);
    }

    @Test
    void verifyFindIdCodeFailsWithIncorrectCode()
        throws Exception {

        when(
            findIdVerificationService.verifyCode(
                "test@gmail.com",
                "111111"
            )
        ).thenThrow(
            new BusinessException(
                ErrorCode.INVALID_FIND_ID_VERIFICATION_CODE
            )
        );

        mockMvc.perform(
            post("/api/v1/auth/find-id/verify-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                    "email": "test@gmail.com",
                    "code": "111111"
                    }
                    """)
        )
            .andExpect(status().isBadRequest());

        verify(findIdVerificationService)
            .verifyCode(
                "test@gmail.com",
                "111111"
            );
    }

    @Test
    void sendPasswordResetVerificationCodeSucceedsWithoutAuthentication()
        throws Exception {

        mockMvc.perform(
            post("/api/v1/auth/password-reset/send-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                    "loginId": "testuser",
                    "email": "test@gmail.com"
                    }
                    """)
        )
            .andExpect(status().isNoContent());

        verify(passwordResetVerificationService)
            .sendVerificationCode(
                "testuser",
                "test@gmail.com",
                "127.0.0.1"
            );
    }

    @Test
    void verifyPasswordResetCodeReturnsResetToken()
        throws Exception {

        when(
            passwordResetVerificationService
                .verifyCode(
                    "testuser",
                    "test@gmail.com",
                    "123456"
                )
        ).thenReturn("test-reset-token");

        mockMvc.perform(
            post("/api/v1/auth/password-reset/verify-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                    "loginId": "testuser",
                    "email": "test@gmail.com",
                    "code": "123456"
                    }
                    """)
        )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.resetToken")
                    .value("test-reset-token")
            );

        verify(passwordResetVerificationService)
            .verifyCode(
                "testuser",
                "test@gmail.com",
                "123456"
            );
    }

    @Test
    void verifyPasswordResetCodeFailsWithInvalidCodeFormat()
        throws Exception {

        mockMvc.perform(
            post("/api/v1/auth/password-reset/verify-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                    "loginId": "testuser",
                    "email": "test@gmail.com",
                    "code": "123"
                    }
                    """)
        )
            .andExpect(status().isBadRequest());

        verifyNoInteractions(
            passwordResetVerificationService
        );
    }

    @Test
    void resetPasswordSucceedsWithoutAuthentication()
        throws Exception {

        mockMvc.perform(
            post("/api/v1/auth/password-reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                    "resetToken": "test-reset-token",
                    "newPassword": "Tripbler456!"
                    }
                    """)
        )
            .andExpect(status().isNoContent());

        verify(passwordResetService)
            .resetPassword(
                "test-reset-token",
                "Tripbler456!"
            );
    }
}