package com.tripbler.backend.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.tripbler.backend.auth.service.AccountDeletionService;
import com.tripbler.backend.common.config.SecurityConfig;
import com.tripbler.backend.common.security.CustomAccessDeniedHandler;
import com.tripbler.backend.common.security.CustomAuthenticationEntryPoint;
import com.tripbler.backend.user.dto.UserResponse;
import com.tripbler.backend.user.service.ProfileImageService;
import com.tripbler.backend.user.service.UserService;

@WebMvcTest(UserController.class)
@Import({
    SecurityConfig.class,
    CustomAuthenticationEntryPoint.class,
    CustomAccessDeniedHandler.class
})
class UserProfileImageControllerTest {

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
        "JWT 인증 사용자가 프로필 이미지를 업로드하면 "
            + "200과 수정된 사용자 정보를 반환한다"
    )
    void updateProfileImageReturnsUpdatedUser()
        throws Exception {

        Long userId = 1L;

        MockMultipartFile file =
            new MockMultipartFile(
                "file",
                "profile.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "image-data".getBytes()
            );

        String profileImageUrl =
            "http://localhost:8080/uploads/"
                + "profiles/1/profile.jpg";

        when(
            profileImageService.updateProfileImage(
                eq(userId),
                any()
            )
        ).thenReturn(
            new UserResponse(
                userId,
                "testuser01",
                "여행자",
                profileImageUrl
            )
        );

        mockMvc.perform(
                multipart(
                    "/api/v1/users/me/profile-image"
                )
                    .file(file)
                    .with(
                        request -> {
                            request.setMethod("PUT");
                            return request;
                        }
                    )
                    .with(
                        jwt().jwt(
                            token -> token.subject(
                                userId.toString()
                            )
                        )
                    )
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
                    .value("여행자")
            )
            .andExpect(
                jsonPath("$.profileImageUrl")
                    .value(profileImageUrl)
            );

        verify(profileImageService)
            .updateProfileImage(
                eq(userId),
                any()
            );
    }

    @Test
    @DisplayName(
        "JWT 인증 사용자가 프로필 이미지를 삭제하면 "
            + "204 NO_CONTENT를 반환한다"
    )
    void deleteProfileImageReturnsNoContent()
        throws Exception {

        Long userId = 1L;

        mockMvc.perform(
                delete(
                    "/api/v1/users/me/profile-image"
                )
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

        verify(profileImageService)
            .deleteProfileImage(userId);
    }

    @Test
    @DisplayName(
        "JWT 없이 프로필 이미지 업로드를 요청하면 "
            + "401 UNAUTHORIZED를 반환한다"
    )
    void updateProfileImageWithoutJwtReturnsUnauthorized()
        throws Exception {

        MockMultipartFile file =
            new MockMultipartFile(
                "file",
                "profile.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "image-data".getBytes()
            );

        mockMvc.perform(
                multipart(
                    "/api/v1/users/me/profile-image"
                )
                    .file(file)
                    .with(
                        request -> {
                            request.setMethod("PUT");
                            return request;
                        }
                    )
            )
            .andExpect(
                status().isUnauthorized()
            )
            .andExpect(
                jsonPath("$.status")
                    .value(401)
            )
            .andExpect(
                jsonPath("$.code")
                    .value("UNAUTHORIZED")
            );

        verify(
            profileImageService,
            never()
        ).updateProfileImage(
            anyLong(),
            any()
        );
    }
}