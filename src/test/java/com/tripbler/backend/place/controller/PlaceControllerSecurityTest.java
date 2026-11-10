package com.tripbler.backend.place.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.tripbler.backend.common.config.SecurityConfig;
import com.tripbler.backend.common.exception.GlobalExceptionHandler;
import com.tripbler.backend.common.security.CustomAccessDeniedHandler;
import com.tripbler.backend.common.security.CustomAuthenticationEntryPoint;
import com.tripbler.backend.place.dto.PlaceResponse;
import com.tripbler.backend.place.service.PlaceService;

// 실제 SecurityConfig를 불러와 장소 API의 인증 규칙을 확인한다.
// /search만 로그인이 필요하고 나머지 장소 API는 공개다.
@WebMvcTest(PlaceController.class)
@Import({
    SecurityConfig.class,
    CustomAuthenticationEntryPoint.class,
    CustomAccessDeniedHandler.class,
    GlobalExceptionHandler.class
})
class PlaceControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlaceService placeService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void 토큰_없이_검색하면_401을_반환한다() throws Exception {
        mockMvc.perform(
                get("/api/v1/places/search")
                    .param("query", "경복궁")
            )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.message").value("인증이 필요합니다."))
            .andExpect(jsonPath("$.path").value("/api/v1/places/search"));

        verifyNoInteractions(placeService);
    }

    @Test
    void 만료된_토큰으로_검색하면_401을_반환하고_서비스를_호출하지_않는다() throws Exception {
        // 실제 JwtDecoder가 만료된 토큰을 검증할 때 던지는 예외를 흉내 낸다.
        when(jwtDecoder.decode(anyString())).thenThrow(
            new JwtValidationException(
                "Jwt expired",
                List.of(
                    new OAuth2Error(
                        "invalid_token",
                        "Jwt expired at 2026-01-01T00:00:00Z",
                        null
                    )
                )
            )
        );

        mockMvc.perform(
                get("/api/v1/places/search")
                    .header("Authorization", "Bearer expired-access-token")
                    .param("query", "경복궁")
            )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.path").value("/api/v1/places/search"));

        verify(jwtDecoder).decode("expired-access-token");
        verifyNoInteractions(placeService);
    }

    @Test
    void 토큰이_있으면_검색이_상세_조회가_아닌_검색으로_처리된다() throws Exception {
        // /search가 /{placeId}("search"라는 ID)로 처리되지 않아야 한다.
        when(
            placeService.searchText("경복궁", null, null)
        ).thenReturn(List.of());

        mockMvc.perform(
                get("/api/v1/places/search")
                    .with(jwt())
                    .param("query", "경복궁")
            )
            .andExpect(status().isOk());

        verify(placeService).searchText("경복궁", null, null);
        verify(placeService, never()).getPlaceDetails("search");
    }

    @Test
    void 주변_검색은_토큰_없이_호출할_수_있다() throws Exception {
        when(
            placeService.searchNearby(37.5665, 126.978, "tourist_attraction", 1500)
        ).thenReturn(List.of());

        mockMvc.perform(
                get("/api/v1/places/nearby")
                    .param("lat", "37.5665")
                    .param("lng", "126.978")
            )
            .andExpect(status().isOk());
    }

    @Test
    void 최근접_조회는_토큰_없이_호출할_수_있다() throws Exception {
        when(
            placeService.findNearestPlace(37.5665, 126.978, 40)
        ).thenReturn(Optional.empty());

        mockMvc.perform(
                get("/api/v1/places/nearest")
                    .param("lat", "37.5665")
                    .param("lng", "126.978")
            )
            .andExpect(status().isNoContent());
    }

    @Test
    void 상세_조회는_토큰_없이_호출할_수_있다() throws Exception {
        when(
            placeService.getPlaceDetails("place-1")
        ).thenReturn(
            new PlaceResponse(
                "place-1", "경복궁", 37.5796, 126.977,
                "서울특별시 종로구 사직로 161", 4.6, "tourist_attraction", true
            )
        );

        mockMvc.perform(
                get("/api/v1/places/place-1")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("place-1"));
    }
}
