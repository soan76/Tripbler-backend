package com.tripbler.backend.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.tripbler.backend.auth.service.AccountDeletionService;
import com.tripbler.backend.common.config.SecurityConfig;
import com.tripbler.backend.common.security.CustomAccessDeniedHandler;
import com.tripbler.backend.common.security.CustomAuthenticationEntryPoint;
import com.tripbler.backend.user.dto.UserNicknameChangeRequest;
import com.tripbler.backend.user.dto.UserResponse;
import com.tripbler.backend.user.exception.DuplicateNicknameException;
import com.tripbler.backend.user.service.ProfileImageService;
import com.tripbler.backend.user.service.UserService;

@WebMvcTest(UserController.class)
@Import({
    SecurityConfig.class,
    CustomAuthenticationEntryPoint.class,
    CustomAccessDeniedHandler.class
})
class UserNicknameControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private ProfileImageService profileImageService;

    @MockitoBean
    private AccountDeletionService accountDeletionService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName(
        "JWT 인증 사용자가 닉네임을 변경하면 "
            + "200과 수정된 사용자 정보를 반환한다"
    )
    void changeNicknameReturnsUpdatedUser()
        throws Exception {

        Long userId = 1L;

        when(
            userService.changeNickname(
                eq(userId),
                argThat(
                    request ->
                        request != null
                            && "새닉네임".equals(
                                request.nickname()
                            )
                )
            )
        ).thenReturn(
            new UserResponse(
                userId,
                "testuser01",
                "새닉네임",
                null
            )
        );

        mockMvc.perform(
                patch("/api/v1/users/me/nickname")
                    .with(
                        jwt().jwt(
                            token -> token.subject(
                                userId.toString()
                            )
                        )
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "nickname": "  새닉네임  "
                        }
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.id")
                    .value(userId)
            )
            .andExpect(
                jsonPath("$.loginId")
                    .value("testuser01")
            )
            .andExpect(
                jsonPath("$.nickname")
                    .value("새닉네임")
            );

        verify(userService)
            .changeNickname(
                eq(userId),
                argThat(
                    request ->
                        request != null
                            && "새닉네임".equals(
                                request.nickname()
                            )
                )
            );
    }

    @Test
    @DisplayName(
        "다른 사용자가 사용 중인 닉네임으로 변경하면 "
            + "409 CONFLICT를 반환한다"
    )
    void changeNicknameWithDuplicateNicknameReturnsConflict()
        throws Exception {

        Long userId = 1L;

        when(
            userService.changeNickname(
                eq(userId),
                any(UserNicknameChangeRequest.class)
            )
        ).thenThrow(
            new DuplicateNicknameException()
        );

        mockMvc.perform(
                patch("/api/v1/users/me/nickname")
                    .with(
                        jwt().jwt(
                            token -> token.subject(
                                userId.toString()
                            )
                        )
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "nickname": "사용중닉네임"
                        }
                        """)
            )
            .andExpect(status().isConflict())
            .andExpect(
                jsonPath("$.status")
                    .value(409)
            )
            .andExpect(
                jsonPath("$.code")
                    .value("DUPLICATE_NICKNAME")
            )
            .andExpect(
                jsonPath("$.message")
                    .value("이미 사용 중인 닉네임입니다.")
            );
    }

    @Test
    @DisplayName(
        "빈 닉네임으로 변경을 요청하면 "
            + "400 BAD_REQUEST를 반환한다"
    )
    void changeNicknameWithBlankNicknameReturnsBadRequest()
        throws Exception {

        performInvalidNicknameChange("   ");

        verifyChangeNicknameWasNotCalled();
    }

    @Test
    @DisplayName("1자 닉네임으로 변경할 수 있다")
    void changeNicknameWithOneCharacterReturnsOk()
        throws Exception {

        Long userId = 1L;

        when(
            userService.changeNickname(
                eq(userId),
                argThat(
                    request ->
                        request != null
                            && "가".equals(
                                request.nickname()
                            )
                )
            )
        ).thenReturn(
            new UserResponse(
                userId,
                "testuser01",
                "가",
                null
            )
        );

        mockMvc.perform(
                patch("/api/v1/users/me/nickname")
                    .with(
                        jwt().jwt(
                            token -> token.subject(
                                userId.toString()
                            )
                        )
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "nickname": "가"
                        }
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.nickname")
                    .value("가")
            );

        verify(userService)
            .changeNickname(
                eq(userId),
                argThat(
                    request ->
                        request != null
                            && "가".equals(
                                request.nickname()
                            )
                )
            );
    }

    @Test
    @DisplayName(
        "20자를 초과한 닉네임으로 변경을 요청하면 "
            + "400 BAD_REQUEST를 반환한다"
    )
    void changeNicknameWithTooLongNicknameReturnsBadRequest()
        throws Exception {

        performInvalidNicknameChange(
            "123456789012345678901"
        );

        verifyChangeNicknameWasNotCalled();
    }

    @Test
    @DisplayName(
        "JWT 없이 닉네임 변경을 요청하면 "
            + "401 UNAUTHORIZED를 반환한다"
    )
    void changeNicknameWithoutJwtReturnsUnauthorized()
        throws Exception {

        mockMvc.perform(
                patch("/api/v1/users/me/nickname")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "nickname": "새닉네임"
                        }
                        """)
            )
            .andExpect(status().isUnauthorized())
            .andExpect(
                jsonPath("$.status")
                    .value(401)
            )
            .andExpect(
                jsonPath("$.code")
                    .value("UNAUTHORIZED")
            );

        verifyChangeNicknameWasNotCalled();
    }

    private void performInvalidNicknameChange(
        String nickname
    ) throws Exception {

        mockMvc.perform(
                patch("/api/v1/users/me/nickname")
                    .with(
                        jwt().jwt(
                            token -> token.subject("1")
                        )
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "nickname": "%s"
                        }
                        """.formatted(nickname)
                    )
            )
            .andExpect(
                status().isBadRequest()
            );
    }

    private void verifyChangeNicknameWasNotCalled() {
        verify(
            userService,
            never()
        ).changeNickname(
            anyLong(),
            any(UserNicknameChangeRequest.class)
        );
    }
}