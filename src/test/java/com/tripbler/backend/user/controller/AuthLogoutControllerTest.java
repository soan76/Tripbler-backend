package com.tripbler.backend.user.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.tripbler.backend.auth.service.FindIdVerificationService;
import com.tripbler.backend.auth.service.PasswordResetService;
import com.tripbler.backend.auth.service.PasswordResetVerificationService;
import com.tripbler.backend.common.config.SecurityConfig;
import com.tripbler.backend.common.exception.GlobalExceptionHandler;
import com.tripbler.backend.common.security.CustomAccessDeniedHandler;
import com.tripbler.backend.common.security.CustomAuthenticationEntryPoint;
import com.tripbler.backend.user.service.AuthService;

/**
 * 로그아웃 계약: 본문의 Refresh Token으로 처리하고 Access Token 없이 호출할 수 있다.
 * 실제 SecurityConfig와 GlobalExceptionHandler를 불러와 인증 규칙과 오류 응답까지 확인한다.
 */
@WebMvcTest(AuthController.class)
@Import({
    SecurityConfig.class,
    CustomAuthenticationEntryPoint.class,
    CustomAccessDeniedHandler.class,
    GlobalExceptionHandler.class
})
class AuthLogoutControllerTest {

    private static final String LOGOUT_URL = "/api/v1/auth/logout";

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
    void Access_Token_없이_Refresh_Token으로_로그아웃하면_204() throws Exception {
        mockMvc.perform(
                post(LOGOUT_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"refreshToken\":\"refresh-token-of-this-device\"}")
            )
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));

        verify(authService).logout("refresh-token-of-this-device");
        verifyNoInteractions(jwtDecoder);
    }

    @Test
    void Refresh_Token이_비었으면_400이고_폐기하지_않는다() throws Exception {
        mockMvc.perform(
                post(LOGOUT_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"refreshToken\":\"   \"}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.path").value(LOGOUT_URL));

        verifyNoInteractions(authService);
    }

    @Test
    void Refresh_Token이_512자를_넘으면_400() throws Exception {
        String tooLong = "a".repeat(513);

        mockMvc.perform(
                post(LOGOUT_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"refreshToken\":\"" + tooLong + "\"}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(authService);
    }

    @Test
    void 본문이_없으면_500이_아니라_400() throws Exception {
        mockMvc.perform(
                post(LOGOUT_URL)
                    .contentType(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.message").value("요청 본문이 없거나 형식이 올바르지 않습니다."));

        verifyNoInteractions(authService);
    }

    @Test
    void 본문이_JSON이_아니면_400이고_본문_내용을_응답에_담지_않는다() throws Exception {
        mockMvc.perform(
                post(LOGOUT_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"refreshToken\": secret-value")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("secret-value")
            )));

        verifyNoInteractions(authService);
    }

    @Test
    void 이미_폐기됐거나_모르는_토큰이어도_204() throws Exception {
        // 서비스는 일치하는 토큰이 없으면 아무것도 지우지 않고 정상 종료한다.
        mockMvc.perform(
                post(LOGOUT_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"refreshToken\":\"already-revoked-token\"}")
            )
            .andExpect(status().isNoContent());

        verify(authService).logout(anyString());
    }
}
