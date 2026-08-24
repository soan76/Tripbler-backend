package com.tripbler.backend.common.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import com.tripbler.backend.admin.controller.AdminController;
import com.tripbler.backend.common.security.CustomAccessDeniedHandler;
import com.tripbler.backend.common.security.CustomAuthenticationEntryPoint;
import com.tripbler.backend.user.controller.UserController;
import com.tripbler.backend.user.service.UserService;

@WebMvcTest({
    UserController.class,
    AdminController.class
})
@Import({
    SecurityConfig.class,
    CustomAuthenticationEntryPoint.class,
    CustomAccessDeniedHandler.class
})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("JWT 없이 /users/me에 접근하면 401 UNAUTHORIZED를 반환한다")
    void usersMeWithoutJwtReturns401() throws Exception {

        mockMvc.perform(
                get("/api/v1/users/me")
            )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.message").value("인증이 필요합니다."))
            .andExpect(jsonPath("$.path").value("/api/v1/users/me"));
    }

    @Test
    @DisplayName("USER 권한으로 관리자 API에 접근하면 403 FORBIDDEN을 반환한다")
    void userCannotAccessAdminApi() throws Exception {

        mockMvc.perform(
                get("/api/v1/admin/test")
                    .with(
                        jwt().authorities(
                            new SimpleGrantedAuthority("ROLE_USER")
                        )
                    )
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))
            .andExpect(jsonPath("$.message").value("접근 권한이 없습니다."))
            .andExpect(jsonPath("$.path").value("/api/v1/admin/test"));
    }

    @Test
    @DisplayName("ADMIN 권한으로 관리자 API에 접근하면 200 OK를 반환한다")
    void adminCanAccessAdminApi() throws Exception {

        mockMvc.perform(
                get("/api/v1/admin/test")
                    .with(
                        jwt().authorities(
                            new SimpleGrantedAuthority("ROLE_ADMIN")
                        )
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message")
                .value("관리자 권한 접근 성공"));
    }

    @Test
    @DisplayName("JWT 없이 /auth/logout에 접근하면 401 UNAUTHORIZED를 반환한다")
    void logoutWithoutJwtReturns401() throws Exception {

        mockMvc.perform(
                post("/api/v1/auth/logout")
            )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.message").value("인증이 필요합니다."))
            .andExpect(jsonPath("$.path").value("/api/v1/auth/logout"));
    }
}