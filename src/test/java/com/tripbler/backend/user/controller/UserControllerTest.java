package com.tripbler.backend.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.tripbler.backend.user.dto.LoginIdAvailabilityResponse;
import com.tripbler.backend.user.dto.UserCreateRequest;
import com.tripbler.backend.user.dto.UserNicknameChangeRequest;
import com.tripbler.backend.user.dto.UserResponse;
import com.tripbler.backend.user.service.UserService;

@WebMvcTest(UserController.class)
@Import({
    SecurityConfig.class,
    CustomAuthenticationEntryPoint.class,
    CustomAccessDeniedHandler.class
})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private AccountDeletionService accountDeletionService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("사용 가능한 아이디이면 available=true를 반환한다")
    void checkLoginIdAvailabilityReturnsTrue() throws Exception {

        String loginId = "newuser123";

        when(
            userService.checkLoginIdAvailability(loginId)
        ).thenReturn(
            new LoginIdAvailabilityResponse(
                loginId,
                true
            )
        );

        mockMvc.perform(
                get("/api/v1/users/check-login-id")
                    .param("loginId", loginId)
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.loginId")
                    .value(loginId)
            )
            .andExpect(
                jsonPath("$.available")
                    .value(true)
            );
    }

    @Test
    @DisplayName("이미 존재하는 아이디이면 available=false를 반환한다")
    void checkLoginIdAvailabilityReturnsFalse() throws Exception {

        String loginId = "test0908";

        when(
            userService.checkLoginIdAvailability(loginId)
        ).thenReturn(
            new LoginIdAvailabilityResponse(
                loginId,
                false
            )
        );

        mockMvc.perform(
                get("/api/v1/users/check-login-id")
                    .param("loginId", loginId)
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.loginId")
                    .value(loginId)
            )
            .andExpect(
                jsonPath("$.available")
                    .value(false)
            );
    }

    @Test
    @DisplayName("닉네임 없이 회원가입할 수 있다")
    void createUserWithoutNicknameReturnsCreated() throws Exception {

        when(
            userService.createUser(
                any(UserCreateRequest.class)
            )
        ).thenReturn(
            new UserResponse(
                1L,
                "nonicktest01",
                null
            )
        );

        mockMvc.perform(
                post("/api/v1/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "loginId": "nonicktest01",
                          "password": "password123"
                        }
                        """)
            )
            .andExpect(status().isCreated())
            .andExpect(
                jsonPath("$.loginId")
                    .value("nonicktest01")
            );
    }

    @Test
    @DisplayName("1자 닉네임으로 회원가입할 수 있다")
    void createUserWithOneCharacterNicknameReturnsCreated()
        throws Exception {

        when(
            userService.createUser(
                any(UserCreateRequest.class)
            )
        ).thenReturn(
            new UserResponse(
                1L,
                "nicktest01",
                "가"
            )
        );

        mockMvc.perform(
                post("/api/v1/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "loginId": "nicktest01",
                          "nickname": "가",
                          "password": "password123"
                        }
                        """)
            )
            .andExpect(status().isCreated())
            .andExpect(
                jsonPath("$.loginId")
                    .value("nicktest01")
            )
            .andExpect(
                jsonPath("$.nickname")
                    .value("가")
            );

        verify(userService)
            .createUser(
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
    @DisplayName("닉네임이 20자를 초과하면 회원가입 요청은 400 BAD_REQUEST를 반환한다")
    void createUserWithTooLongNicknameReturnsBadRequest()
        throws Exception {

        mockMvc.perform(
                post("/api/v1/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "loginId": "nicktest02",
                          "nickname": "123456789012345678901",
                          "password": "password123"
                        }
                        """)
            )
            .andExpect(status().isBadRequest());

        verify(
            userService,
            never()
        ).createUser(
            any(UserCreateRequest.class)
        );
    }

    @Test
    @DisplayName("JWT 인증 사용자가 닉네임을 변경하면 200과 수정된 사용자 정보를 반환한다")
    void changeNicknameReturnsUpdatedUser() throws Exception {

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
                "새닉네임"
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
    @DisplayName("빈 닉네임으로 변경을 요청하면 400 BAD_REQUEST를 반환한다")
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
                "가"
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
    @DisplayName("20자를 초과한 닉네임으로 변경을 요청하면 400 BAD_REQUEST를 반환한다")
    void changeNicknameWithTooLongNicknameReturnsBadRequest()
        throws Exception {

        performInvalidNicknameChange(
            "123456789012345678901"
        );

        verifyChangeNicknameWasNotCalled();
    }

    @Test
    @DisplayName("JWT 없이 닉네임 변경을 요청하면 401 UNAUTHORIZED를 반환한다")
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

    @Test
    @DisplayName("JWT 인증 사용자가 계정 탈퇴를 요청하면 204 NO_CONTENT를 반환한다")
    void deleteCurrentUserReturnsNoContent() throws Exception {

        Long userId = 1L;

        mockMvc.perform(
                delete("/api/v1/users/me")
                    .with(
                        jwt().jwt(
                            token -> token.subject(
                                userId.toString()
                            )
                        )
                    )
            )
            .andExpect(
                status().isNoContent()
            );

        verify(
            accountDeletionService
        ).deleteAccount(
            userId
        );
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
            .andExpect(status().isBadRequest());
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