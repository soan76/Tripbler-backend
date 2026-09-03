package com.tripbler.backend.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import com.tripbler.backend.auth.service.AccountDeletionService;
import com.tripbler.backend.common.config.SecurityConfig;
import com.tripbler.backend.common.security.CustomAccessDeniedHandler;
import com.tripbler.backend.common.security.CustomAuthenticationEntryPoint;
import com.tripbler.backend.user.dto.LoginIdAvailabilityResponse;
import com.tripbler.backend.user.dto.UserCreateRequest;
import com.tripbler.backend.user.dto.UserResponse;
import com.tripbler.backend.user.service.UserService;

@WebMvcTest(UserController.class)
@Import({
    SecurityConfig.class,
    CustomAuthenticationEntryPoint.class,
    CustomAccessDeniedHandler.class
})
class UserControllerTest {
    // MockMvc를 주입받아 테스트를 수행한다.
    @Autowired
    private MockMvc mockMvc;
    // 사용자 서비스 모킹
    @MockitoBean
    private UserService userService;
    // 계정 삭제 서비스 모킹
    @MockitoBean
    private AccountDeletionService accountDeletionService;
    // JWT 디코더 모킹
    @MockitoBean
    private JwtDecoder jwtDecoder;
    // 로그인 ID 중복 여부를 확인한다
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
    // JWT 인증 없이 /users/me에 접근하면 401 UNAUTHORIZED를 반환한다
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
    // JWT 인증 없이 /users/me에 접근하면 401 UNAUTHORIZED를 반환한다
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
            )
            .andExpect(
                jsonPath("$.nickname")
                    .doesNotExist()
            );
    }
    // 닉네임이 1자여도 회원가입할 수 있다
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
                2L,
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
    }
    // 닉네임이 20자를 초과하면 400을 반환한다
    @Test
    @DisplayName("닉네임이 20자를 초과하면 400을 반환한다")
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
    }
    
    // JWT 인증 사용자가 계정 탈퇴를 요청하면 204 NO_CONTENT를 반환한다
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
        .andDo(print())
        .andExpect(
            status().isNoContent()
        );

        verify(
            accountDeletionService
        ).deleteAccount(
            userId
        );
    }
}