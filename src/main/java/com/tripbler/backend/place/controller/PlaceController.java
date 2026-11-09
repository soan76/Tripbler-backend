package com.tripbler.backend.place.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tripbler.backend.place.dto.PlaceResponse;
import com.tripbler.backend.place.service.PlaceService;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

// 클래스에 @Validated를 붙이지 않는다.
// 파라미터 제약만 두면 Spring MVC 기본 검증이 HandlerMethodValidationException을 던지고,
// GlobalExceptionHandler가 400 INVALID_REQUEST로 응답한다.
// 좌표는 NaN·Infinity를 형식 오류(400)로 막기 위해 BigDecimal로 받는다.
@RestController
@RequestMapping("/api/v1/places")
public class PlaceController {

    private static final String LATITUDE_MESSAGE =
        "위도는 -90~90 사이여야 합니다.";

    private static final String LONGITUDE_MESSAGE =
        "경도는 -180~180 사이여야 합니다.";

    private static final String RADIUS_MESSAGE =
        "반경은 1~50000m 사이여야 합니다.";

    private final PlaceService placeService;

    public PlaceController(
        PlaceService placeService
    ) {
        this.placeService = placeService;
    }

    @GetMapping("/nearby")
    public List<PlaceResponse> searchNearby(
        @RequestParam("lat")
        @DecimalMin(value = "-90", message = LATITUDE_MESSAGE)
        @DecimalMax(value = "90", message = LATITUDE_MESSAGE)
        BigDecimal latitude,

        @RequestParam("lng")
        @DecimalMin(value = "-180", message = LONGITUDE_MESSAGE)
        @DecimalMax(value = "180", message = LONGITUDE_MESSAGE)
        BigDecimal longitude,

        // 생략하거나 빈 값이면 defaultValue가 적용된다.
        @RequestParam(defaultValue = "tourist_attraction")
        @Pattern(
            regexp = "tourist_attraction|restaurant",
            message = "장소 유형은 tourist_attraction 또는 restaurant만 사용할 수 있습니다."
        )
        String type,

        @RequestParam(defaultValue = "1500")
        @Min(value = 1, message = RADIUS_MESSAGE)
        @Max(value = 50000, message = RADIUS_MESSAGE)
        int radius
    ) {
        return placeService.searchNearby(
            latitude.doubleValue(),
            longitude.doubleValue(),
            type,
            radius
        );
    }

    @GetMapping("/nearest")
    public ResponseEntity<PlaceResponse> getNearestPlace(
        @RequestParam("lat")
        @DecimalMin(value = "-90", message = LATITUDE_MESSAGE)
        @DecimalMax(value = "90", message = LATITUDE_MESSAGE)
        BigDecimal latitude,

        @RequestParam("lng")
        @DecimalMin(value = "-180", message = LONGITUDE_MESSAGE)
        @DecimalMax(value = "180", message = LONGITUDE_MESSAGE)
        BigDecimal longitude,

        @RequestParam(defaultValue = "40")
        @Min(value = 1, message = RADIUS_MESSAGE)
        @Max(value = 50000, message = RADIUS_MESSAGE)
        int radius
    ) {
        return placeService.findNearestPlace(
                latitude.doubleValue(),
                longitude.doubleValue(),
                radius
            )
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{placeId}")
    public PlaceResponse getPlaceDetails(
        @PathVariable String placeId
    ) {
        return placeService.getPlaceDetails(placeId);
    }
}
