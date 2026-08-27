package com.tripbler.backend.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.tripbler.backend.user.dto.LoginIdAvailabilityResponse;
import com.tripbler.backend.user.dto.UserCreateRequest;
import com.tripbler.backend.user.dto.UserResponse;
import com.tripbler.backend.user.service.UserService;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

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
            )
            .andExpect(
                jsonPath("$.nickname")
                    .doesNotExist()
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
}