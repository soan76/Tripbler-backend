package com.tripbler.backend.place.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.SocketTimeoutException;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.tripbler.backend.common.exception.BusinessException;
import com.tripbler.backend.common.exception.ErrorCode;
import com.tripbler.backend.common.exception.GlobalExceptionHandler;
import com.tripbler.backend.common.exception.PlacesProviderException;
import com.tripbler.backend.place.service.PlaceService;

@WebMvcTest(PlaceController.class)
@Import(GlobalExceptionHandler.class)
class PlaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlaceService placeService;

    @Test
    void 최근접_장소가_없으면_204를_반환한다() throws Exception {
        when(
            placeService.findNearestPlace(37.5665, 126.978, 40)
        ).thenReturn(Optional.empty());

        mockMvc.perform(
                get("/api/v1/places/nearest")
                    .param("lat", "37.5665")
                    .param("lng", "126.978")
            )
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));
    }

    @Test
    void 최근접_조회가_시간_초과되면_503을_반환한다() throws Exception {
        when(
            placeService.findNearestPlace(37.5665, 126.978, 40)
        ).thenThrow(
            new PlacesProviderException(
                new SocketTimeoutException("Read timed out")
            )
        );

        mockMvc.perform(
                get("/api/v1/places/nearest")
                    .param("lat", "37.5665")
                    .param("lng", "126.978")
            )
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.status").value(503))
            .andExpect(
                jsonPath("$.code")
                    .value("PLACES_PROVIDER_UNAVAILABLE")
            )
            .andExpect(
                jsonPath("$.path")
                    .value("/api/v1/places/nearest")
            );
    }

    @Test
    void 상세_조회_대상이_없으면_404를_반환한다() throws Exception {
        when(
            placeService.getPlaceDetails("unknown-id")
        ).thenThrow(
            new BusinessException(
                ErrorCode.RESOURCE_NOT_FOUND,
                "요청한 장소를 찾을 수 없습니다."
            )
        );

        mockMvc.perform(get("/api/v1/places/unknown-id"))
            .andExpect(status().isNotFound())
            .andExpect(
                jsonPath("$.code").value("RESOURCE_NOT_FOUND")
            );
    }

    @Test
    void 주변_검색에서_위도가_없으면_400을_반환한다() throws Exception {
        // 필수 파라미터 누락은 500이 아니라 400이어야 한다.
        mockMvc.perform(
                get("/api/v1/places/nearby")
                    .param("lng", "126.978")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(
                jsonPath("$.message")
                    .value("필수 요청 파라미터가 없습니다: lat")
            )
            .andExpect(
                jsonPath("$.path")
                    .value("/api/v1/places/nearby")
            );

        verifyNoInteractions(placeService);
    }

    // 경계값마다 한 값만 바꿔 실패 원인을 구분한다.
    @ParameterizedTest
    @CsvSource({
        "-90, 126.978, 1500",
        "90, 126.978, 1500",
        "+90, 126.978, 1500",
        "37.5665, -180, 1500",
        "37.5665, 180, 1500",
        "37.5665, 126.978, 1",
        "37.5665, 126.978, 50000"
    })
    void 주변_검색은_경계값을_허용한다(
        String lat,
        String lng,
        int radius
    ) throws Exception {
        mockMvc.perform(
                get("/api/v1/places/nearby")
                    .param("lat", lat)
                    .param("lng", lng)
                    .param("radius", String.valueOf(radius))
            )
            .andExpect(status().isOk());

        verify(placeService).searchNearby(
            Double.parseDouble(lat),
            Double.parseDouble(lng),
            "tourist_attraction",
            radius
        );
    }

    @ParameterizedTest
    @CsvSource({
        "90.0001, 0, 1500, 위도는 -90~90 사이여야 합니다.",
        "-90.0001, 0, 1500, 위도는 -90~90 사이여야 합니다.",
        "1e999999999, 0, 1500, 위도는 -90~90 사이여야 합니다.",
        "0, 180.0001, 1500, 경도는 -180~180 사이여야 합니다.",
        "0, -180.0001, 1500, 경도는 -180~180 사이여야 합니다.",
        "0, 0, 0, 반경은 1~50000m 사이여야 합니다.",
        "0, 0, 50001, 반경은 1~50000m 사이여야 합니다."
    })
    void 주변_검색은_범위_밖_값을_400으로_거부한다(
        String lat,
        String lng,
        String radius,
        String message
    ) throws Exception {
        mockMvc.perform(
                get("/api/v1/places/nearby")
                    .param("lat", lat)
                    .param("lng", lng)
                    .param("radius", radius)
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.message").value(message));

        verifyNoInteractions(placeService);
    }

    @Test
    void 주변_검색에서_위도와_경도가_모두_틀리면_두_메시지를_함께_반환한다() throws Exception {
        // 메시지 순서는 보장하지 않으므로 포함 여부만 확인한다.
        mockMvc.perform(
                get("/api/v1/places/nearby")
                    .param("lat", "90.0001")
                    .param("lng", "180.0001")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(
                jsonPath("$.message")
                    .value(containsString("위도는 -90~90 사이여야 합니다."))
            )
            .andExpect(
                jsonPath("$.message")
                    .value(containsString("경도는 -180~180 사이여야 합니다."))
            )
            .andExpect(jsonPath("$.message").value(containsString(", ")));

        verifyNoInteractions(placeService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"NaN", "Infinity", "-Infinity", "abc"})
    void 주변_검색은_숫자가_아닌_좌표를_400으로_거부한다(
        String lat
    ) throws Exception {
        // double로 받으면 NaN·Infinity가 숫자로 변환되므로 BigDecimal로 막는다.
        mockMvc.perform(
                get("/api/v1/places/nearby")
                    .param("lat", lat)
                    .param("lng", "126.978")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(
                jsonPath("$.message")
                    .value("요청 파라미터 형식이 올바르지 않습니다.")
            );

        verifyNoInteractions(placeService);
    }

    @Test
    void 주변_검색에서_type을_생략하면_관광지로_조회한다() throws Exception {
        mockMvc.perform(
                get("/api/v1/places/nearby")
                    .param("lat", "37.5665")
                    .param("lng", "126.978")
            )
            .andExpect(status().isOk());

        verify(placeService).searchNearby(
            37.5665,
            126.978,
            "tourist_attraction",
            1500
        );
    }

    @Test
    void 주변_검색에서_type이_빈_값이면_관광지로_조회한다() throws Exception {
        mockMvc.perform(
                get("/api/v1/places/nearby")
                    .param("lat", "37.5665")
                    .param("lng", "126.978")
                    .param("type", "")
            )
            .andExpect(status().isOk());

        verify(placeService).searchNearby(
            37.5665,
            126.978,
            "tourist_attraction",
            1500
        );
    }

    @Test
    void 주변_검색에서_restaurant는_그대로_전달한다() throws Exception {
        mockMvc.perform(
                get("/api/v1/places/nearby")
                    .param("lat", "37.5665")
                    .param("lng", "126.978")
                    .param("type", "restaurant")
            )
            .andExpect(status().isOk());

        verify(placeService).searchNearby(
            37.5665,
            126.978,
            "restaurant",
            1500
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"cafe", "Restaurant", " restaurant", "not_a_place"})
    void 주변_검색은_허용하지_않은_type을_400으로_거부한다(
        String type
    ) throws Exception {
        mockMvc.perform(
                get("/api/v1/places/nearby")
                    .param("lat", "37.5665")
                    .param("lng", "126.978")
                    .param("type", type)
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(
                jsonPath("$.message")
                    .value(
                        "장소 유형은 tourist_attraction 또는 restaurant만 사용할 수 있습니다."
                    )
            );

        verifyNoInteractions(placeService);
    }

    @ParameterizedTest
    @CsvSource({
        "-90, 126.978, 40",
        "90, 126.978, 40",
        "37.5665, -180, 40",
        "37.5665, 180, 40",
        "37.5665, 126.978, 1",
        "37.5665, 126.978, 50000"
    })
    void 최근접_조회는_경계값을_허용한다(
        String lat,
        String lng,
        int radius
    ) throws Exception {
        mockMvc.perform(
                get("/api/v1/places/nearest")
                    .param("lat", lat)
                    .param("lng", lng)
                    .param("radius", String.valueOf(radius))
            )
            .andExpect(status().isNoContent());

        verify(placeService).findNearestPlace(
            Double.parseDouble(lat),
            Double.parseDouble(lng),
            radius
        );
    }

    @ParameterizedTest
    @CsvSource({
        "90.0001, 0, 40, 위도는 -90~90 사이여야 합니다.",
        "0, -180.0001, 40, 경도는 -180~180 사이여야 합니다.",
        "0, 0, 0, 반경은 1~50000m 사이여야 합니다.",
        "0, 0, 50001, 반경은 1~50000m 사이여야 합니다."
    })
    void 최근접_조회는_범위_밖_값을_400으로_거부한다(
        String lat,
        String lng,
        String radius,
        String message
    ) throws Exception {
        mockMvc.perform(
                get("/api/v1/places/nearest")
                    .param("lat", lat)
                    .param("lng", lng)
                    .param("radius", radius)
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.message").value(message));

        verifyNoInteractions(placeService);
    }

    @ParameterizedTest
    @CsvSource({
        "lat, 126.978, lng",
        "lng, 37.5665, lat"
    })
    void 최근접_조회에서_좌표가_없으면_400을_반환한다(
        String presentName,
        String presentValue,
        String missingName
    ) throws Exception {
        mockMvc.perform(
                get("/api/v1/places/nearest")
                    .param(presentName, presentValue)
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(
                jsonPath("$.message")
                    .value("필수 요청 파라미터가 없습니다: " + missingName)
            )
            .andExpect(
                jsonPath("$.path")
                    .value("/api/v1/places/nearest")
            );

        verifyNoInteractions(placeService);
    }

    @ParameterizedTest
    @CsvSource({
        "NaN, 126.978",
        "Infinity, 126.978",
        "-Infinity, 126.978",
        "abc, 126.978",
        "37.5665, NaN",
        "37.5665, Infinity",
        "37.5665, -Infinity",
        "37.5665, abc"
    })
    void 최근접_조회는_숫자가_아닌_좌표를_400으로_거부한다(
        String lat,
        String lng
    ) throws Exception {
        mockMvc.perform(
                get("/api/v1/places/nearest")
                    .param("lat", lat)
                    .param("lng", lng)
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(
                jsonPath("$.message")
                    .value("요청 파라미터 형식이 올바르지 않습니다.")
            );

        verifyNoInteractions(placeService);
    }

    @ParameterizedTest
    @CsvSource({
        "/api/v1/places/nearby, abc",
        "/api/v1/places/nearby, 1.5",
        "/api/v1/places/nearby, 99999999999",
        "/api/v1/places/nearest, abc",
        "/api/v1/places/nearest, 1.5",
        "/api/v1/places/nearest, 99999999999"
    })
    void 반경_형식이_올바르지_않으면_400을_반환한다(
        String path,
        String radius
    ) throws Exception {
        mockMvc.perform(
                get(path)
                    .param("lat", "37.5665")
                    .param("lng", "126.978")
                    .param("radius", radius)
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(
                jsonPath("$.message")
                    .value("요청 파라미터 형식이 올바르지 않습니다.")
            );

        verifyNoInteractions(placeService);
    }
}
