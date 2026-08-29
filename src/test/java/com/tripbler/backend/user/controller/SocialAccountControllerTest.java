package com.tripbler.backend.user.controller;

import com.tripbler.backend.common.config.SecurityConfig;
import com.tripbler.backend.common.security.CustomAccessDeniedHandler;
import com.tripbler.backend.common.security.CustomAuthenticationEntryPoint;
import com.tripbler.backend.user.service.SocialAccountLinkService;
import com.tripbler.backend.user.dto.response.SocialAccountStatusResponse;
import com.tripbler.backend.user.entity.SocialProvider;
import com.tripbler.backend.user.service.SocialAccountService;

import org.junit.jupiter.api.Test;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SocialAccountController.class)
@Import({
    SecurityConfig.class,
    CustomAuthenticationEntryPoint.class,
    CustomAccessDeniedHandler.class
})
class SocialAccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SocialAccountLinkService socialAccountLinkService;

    @MockitoBean
    private SocialAccountService socialAccountService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void linkGoogleAccountSucceeds() throws Exception {

        // JWT의 subject를 실제 구조처럼 Tripbler userId로 설정한다.
        mockMvc.perform(
            post("/api/v1/users/me/social-accounts/google")
                .with(
                    jwt().jwt(token ->
                        token.subject("1")
                    )
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "idToken": "google-id-token"
                    }
                    """)
        )
            .andExpect(status().isNoContent());

        verify(socialAccountLinkService)
            .linkGoogleAccount(
                1L,
                "google-id-token"
            );
    }

    @Test
    void getLinkedSocialAccountsSucceeds() throws Exception {
        SocialAccountStatusResponse response =
            new SocialAccountStatusResponse(
                List.of(SocialProvider.GOOGLE)
            );

        when(
            socialAccountService.getLinkedSocialAccounts(1L)
        ).thenReturn(response);

        mockMvc.perform(
            get("/api/v1/users/me/social-accounts")
                .with(jwt().jwt(token -> token.subject("1")))
        )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.linkedProviders[0]").value("GOOGLE")
            );

        verify(
            socialAccountService
        ).getLinkedSocialAccounts(1L);
    }

    @Test
    void getLinkedSocialAccountsReturnsEmptyList() throws Exception {
        SocialAccountStatusResponse response =
            new SocialAccountStatusResponse(List.of());

        when(
            socialAccountService.getLinkedSocialAccounts(1L)
        ).thenReturn(response);

        mockMvc.perform(
            get("/api/v1/users/me/social-accounts")
                .with(jwt().jwt(token -> token.subject("1")))
        )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.linkedProviders").isArray()
            )
            .andExpect(
                jsonPath("$.linkedProviders").isEmpty()
            );
    }

    @Test
    void getLinkedSocialAccountsFailsWithoutAuthentication()
        throws Exception {

        mockMvc.perform(
            get("/api/v1/users/me/social-accounts")
        )
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(socialAccountService);
    }

    @Test
    void linkGoogleAccountFailsWithoutAuthentication()
        throws Exception {

        mockMvc.perform(
            post("/api/v1/users/me/social-accounts/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "idToken": "google-id-token"
                    }
                    """)
        )
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(
            socialAccountLinkService
        );
    }

    @Test
    void linkGoogleAccountFailsWhenIdTokenIsBlank()
        throws Exception {

        // 인증은 정상이어도 ID Token이 비어 있으면 요청 검증에서 차단한다.
        mockMvc.perform(
            post("/api/v1/users/me/social-accounts/google")
                .with(
                    jwt().jwt(token ->
                        token.subject("1")
                    )
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "idToken": ""
                    }
                    """)
        )
            .andExpect(status().isBadRequest());

        verifyNoInteractions(
            socialAccountLinkService
        );
    }
}