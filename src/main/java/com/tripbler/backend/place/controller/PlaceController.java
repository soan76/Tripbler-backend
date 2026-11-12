package com.tripbler.backend.place.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tripbler.backend.common.exception.BusinessException;
import com.tripbler.backend.common.exception.ErrorCode;
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

    private static final int QUERY_MAX_LENGTH = 100;

    // 유니코드 태그 문자 범위(지역 깃발 이모지에 쓰임). 검색어 앞뒤 정리에서 지우지 않는다.
    private static final int TAG_CHARACTER_FIRST = 0xE0020;
    private static final int TAG_CHARACTER_LAST = 0xE007F;

    private static final String QUERY_MESSAGE =
        "검색어는 공백을 제외하고 1~100자여야 합니다.";


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

    // 로그인이 필요하다(SecurityConfig).
    // 좌표는 둘 다 보내거나 둘 다 생략한다. 좌표가 있으면 그 주변 결과를 우선한다.
    // 고정 경로라서 /{placeId}보다 먼저 선택된다.
    @GetMapping("/search")
    public List<PlaceResponse> searchText(
        @RequestParam("query") String query,

        @RequestParam(value = "lat", required = false)
        @DecimalMin(value = "-90", message = LATITUDE_MESSAGE)
        @DecimalMax(value = "90", message = LATITUDE_MESSAGE)
        BigDecimal latitude,

        @RequestParam(value = "lng", required = false)
        @DecimalMin(value = "-180", message = LONGITUDE_MESSAGE)
        @DecimalMax(value = "180", message = LONGITUDE_MESSAGE)
        BigDecimal longitude
    ) {
        String normalizedQuery = normalizeQuery(query);

        if ((latitude == null) != (longitude == null)) {
            throw new BusinessException(
                ErrorCode.INVALID_REQUEST,
                "위도와 경도는 함께 보내야 합니다."
            );
        }

        return placeService.searchText(
            normalizedQuery,
            latitude != null ? latitude.doubleValue() : null,
            longitude != null ? longitude.doubleValue() : null
        );
    }

    // 앞뒤 공백류(isQueryEdgeBlank)를 제거한 뒤 1~100자인지 확인한다.
    // 길이는 문자(코드 포인트) 수로 센다.
    private static String normalizeQuery(String query) {
        String normalized = trimQueryEdgeBlanks(query);
        int length = normalized.codePointCount(0, normalized.length());

        if (length < 1 || length > QUERY_MAX_LENGTH) {
            throw new BusinessException(
                ErrorCode.INVALID_REQUEST,
                QUERY_MESSAGE
            );
        }

        return normalized;
    }

    // 앞뒤에서 공백류만 지운다. 가운데 문자(이모지 ZWJ 시퀀스 등)는 건드리지 않는다.
    // 정규식 대신 앞뒤에서 한 번씩 훑어 입력 길이에 비례하는 시간으로 처리한다.
    private static String trimQueryEdgeBlanks(String query) {
        int start = 0;
        int end = query.length();

        while (start < end) {
            int codePoint = query.codePointAt(start);
            if (!isQueryEdgeBlank(codePoint)) {
                break;
            }
            start += Character.charCount(codePoint);
        }

        while (end > start) {
            int codePoint = query.codePointBefore(end);
            if (!isQueryEdgeBlank(codePoint)) {
                break;
            }
            end -= Character.charCount(codePoint);
        }

        return query.substring(start, end);
    }

    // Java 공백(Character.isWhitespace), 유니코드 구분자(Z*: NBSP U+00A0, U+3000 등),
    // 서식 문자(Cf: U+200B, U+FEFF 등).
    // 단, 태그 문자(U+E0020~U+E007F)는 지역 깃발 이모지(🏴 + 태그 + 태그 끝)의 일부라
    // 서식 문자여도 지우지 않는다.
    private static boolean isQueryEdgeBlank(int codePoint) {
        if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
            return true;
        }
        if (codePoint >= TAG_CHARACTER_FIRST && codePoint <= TAG_CHARACTER_LAST) {
            return false;
        }
        return Character.getType(codePoint) == Character.FORMAT;
    }

    @GetMapping("/{placeId}")
    public PlaceResponse getPlaceDetails(
        @PathVariable String placeId
    ) {
        return placeService.getPlaceDetails(placeId);
    }
}
