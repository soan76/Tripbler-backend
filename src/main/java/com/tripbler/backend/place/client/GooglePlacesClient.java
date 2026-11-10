package com.tripbler.backend.place.client;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.tripbler.backend.common.exception.BusinessException;
import com.tripbler.backend.common.exception.ErrorCode;
import com.tripbler.backend.common.exception.PlacesProviderException;
import com.tripbler.backend.place.dto.GoogleNearbySearchRequest;
import com.tripbler.backend.place.dto.GoogleNearbySearchResponse;
import com.tripbler.backend.place.dto.GoogleTextSearchRequest;
import com.tripbler.backend.place.dto.PlaceResponse;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class GooglePlacesClient implements PlaceClient {

    private static final String GOOGLE_PLACES_BASE_URL =
        "https://places.googleapis.com";

    private static final String FIELD_MASK =
        "places.id,"
        + "places.displayName,"
        + "places.location,"
        + "places.formattedAddress,"
        + "places.rating,"
        + "places.primaryType,"
        + "places.currentOpeningHours.openNow";

    // 검색 전용 필드 마스크. Text Search Pro SKU 범위의 필드만 요청한다.
    // rating, currentOpeningHours는 Enterprise SKU라서 넣지 않으며
    // 검색 응답에서 두 값은 null이다.
    static final String SEARCH_TEXT_FIELD_MASK =
        "places.id,"
        + "places.displayName,"
        + "places.location,"
        + "places.formattedAddress,"
        + "places.primaryType";

    // 검색 결과를 우선할 주변 반경(m). 결과를 이 범위로 제한하지는 않는다.
    static final double SEARCH_LOCATION_BIAS_RADIUS_METERS = 5000;

    private static final int SEARCH_PAGE_SIZE = 20;

    private static final String SEARCH_LANGUAGE_CODE = "ko";

    private static final Logger log =
        LoggerFactory.getLogger(GooglePlacesClient.class);

    // Google 오류 본문(error.details[].reason)을 읽기 위한 파서.
    private static final JsonMapper JSON_MAPPER =
        JsonMapper.builder().build();

    // Google이 400으로 응답해도 사용자 입력이 아니라
    // 서버 설정(API 키, 프로젝트의 API 사용 설정) 문제를 뜻하는 ErrorInfo.reason.
    // - API_KEY_INVALID: 키 값이 잘못됨
    // - API_KEY_SERVICE_BLOCKED: 키 제한으로 Places API 호출이 막힘
    // - SERVICE_DISABLED: 프로젝트에서 Places API가 사용 설정되지 않음
    private static final Set<String> SERVER_CONFIG_ERROR_REASONS =
        Set.of(
            "API_KEY_INVALID",
            "API_KEY_SERVICE_BLOCKED",
            "SERVICE_DISABLED"
        );

    // 앱의 장소 요청 제한 시간(8초)보다 먼저 끝나도록
    // 연결 + 읽기 합계를 7초로 둔다.
    // 테스트에서 값을 확인할 수 있도록 package-private으로 둔다.
    static final Duration CONNECT_TIMEOUT =
        Duration.ofSeconds(2);

    static final Duration READ_TIMEOUT =
        Duration.ofSeconds(5);

    private final RestClient restClient;
    private final String apiKey;

    @Autowired
    public GooglePlacesClient(
        RestClient.Builder restClientBuilder,
        @Value("${google.places.api-key}") String apiKey
    ) {
        this(
            restClientBuilder,
            GOOGLE_PLACES_BASE_URL,
            apiKey,
            CONNECT_TIMEOUT,
            READ_TIMEOUT
        );
    }

    // 운영 생성자와 같은 방식으로 타임아웃을 건다.
    // 테스트에서 로컬 서버 주소와 짧은 타임아웃으로 연결 경로를 검증한다.
    GooglePlacesClient(
        RestClient.Builder restClientBuilder,
        String baseUrl,
        String apiKey,
        Duration connectTimeout,
        Duration readTimeout
    ) {
        SimpleClientHttpRequestFactory requestFactory =
            new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);

        this.restClient = restClientBuilder
            .baseUrl(baseUrl)
            .requestFactory(requestFactory)
            .build();

        this.apiKey = apiKey;
    }

    // 테스트에서 MockRestServiceServer에 연결된 RestClient를 주입한다.
    GooglePlacesClient(
        RestClient restClient,
        String apiKey
    ) {
        this.restClient = restClient;
        this.apiKey = apiKey;
    }

    @Override
    public List<PlaceResponse> searchNearby(
        double latitude,
        double longitude,
        String type,
        int radius
    ) {
        GoogleNearbySearchRequest request =
            createRequest(
                latitude,
                longitude,
                type,
                radius
            );

        try {
            GoogleNearbySearchResponse response =
                restClient
                    .post()
                    .uri("/v1/places:searchNearby")
                    .header(
                        "X-Goog-Api-Key",
                        apiKey
                    )
                    .header(
                        "X-Goog-FieldMask",
                        FIELD_MASK
                    )
                    .body(request)
                    .retrieve()
                    .body(
                        GoogleNearbySearchResponse.class
                    );

            if (
                response == null
                    || response.places() == null
            ) {
                return List.of();
            }

            return response
                .places()
                .stream()
                .map(this::toPlaceResponse)
                .toList();

        } catch (RestClientException exception) {
            throw toSearchException(exception);
        }
    }

    private List<PlaceResponse> searchNearbyWithoutType(
        double latitude,
        double longitude,
        int radius
    ) {
        GoogleNearbySearchRequest request =
            createRequestWithoutType(
                latitude,
                longitude,
                radius
            );  

        try {
            GoogleNearbySearchResponse response =
                restClient
                    .post()
                    .uri("/v1/places:searchNearby")
                    .header(
                        "X-Goog-Api-Key",
                        apiKey
                    )
                    .header(
                        "X-Goog-FieldMask",
                        FIELD_MASK
                    )
                    .body(request)
                    .retrieve()
                    .body(
                        GoogleNearbySearchResponse.class
                    );

            if (
                response == null
                    || response.places() == null
            ) {
                return List.of();
            }

            return response
                .places()
                .stream()
                .map(this::toPlaceResponse)
                .toList();

        } catch (RestClientException exception) {
            throw toSearchException(exception);
        }
    }

    private GoogleNearbySearchRequest createRequestWithoutType(
        double latitude,
        double longitude,
        int radius
    ) {
        var center =
            new GoogleNearbySearchRequest.Center(
                latitude,
                longitude
            );

        var circle =
            new GoogleNearbySearchRequest.Circle(
                center,
                radius
            );

        var restriction =
            new GoogleNearbySearchRequest
                .LocationRestriction(circle);

        return new GoogleNearbySearchRequest(
            null,
            20,
            restriction
        );
    }

    @Override
    public PlaceResponse findNearestPlace(
        double latitude,
        double longitude,
        int radius
    ) {
        List<PlaceResponse> places =
            searchNearbyWithoutType(
                latitude,
                longitude,
                radius
            );

        if (places.isEmpty()) {
            return null;
        }

        return places
            .stream()
            .min(
                java.util.Comparator.comparingDouble(
                    place -> calculateDistance(
                        latitude,
                        longitude,
                        place.latitude(),
                        place.longitude()
                    )
                )
            )
            .orElse(null);
    }

    @Override
    public PlaceResponse getPlaceDetails(
        String placeId
    ) {
        try {
            GoogleNearbySearchResponse.GooglePlace place =
                restClient
                    .get()
                    .uri(
                        "/v1/places/{placeId}",
                        placeId
                    )
                    .header(
                        "X-Goog-Api-Key",
                        apiKey
                    )
                    .header(
                        "X-Goog-FieldMask",
                        "id,displayName,location,"
                            + "formattedAddress,rating,"
                            + "primaryType,"
                            + "currentOpeningHours.openNow"
                    )
                    .retrieve()
                    .body(
                        GoogleNearbySearchResponse
                            .GooglePlace.class
                    );

            if (place == null) {
                throw new PlacesProviderException(
                    new IllegalStateException(
                        "Google Place Details 응답이 비어 있습니다."
                    )
                );
            }

            return toPlaceResponse(place);

        } catch (RestClientException exception) {
            throw toPlaceDetailsException(exception);
        }
    }

    @Override
    public List<PlaceResponse> searchText(
        String query,
        Double latitude,
        Double longitude
    ) {
        GoogleTextSearchRequest.LocationBias locationBias = null;

        if (latitude != null && longitude != null) {
            locationBias =
                new GoogleTextSearchRequest.LocationBias(
                    new GoogleNearbySearchRequest.Circle(
                        new GoogleNearbySearchRequest.Center(
                            latitude,
                            longitude
                        ),
                        SEARCH_LOCATION_BIAS_RADIUS_METERS
                    )
                );
        }

        GoogleTextSearchRequest request =
            new GoogleTextSearchRequest(
                query,
                SEARCH_LANGUAGE_CODE,
                SEARCH_PAGE_SIZE,
                locationBias
            );

        try {
            GoogleNearbySearchResponse response =
                restClient
                    .post()
                    .uri("/v1/places:searchText")
                    .header(
                        "X-Goog-Api-Key",
                        apiKey
                    )
                    .header(
                        "X-Goog-FieldMask",
                        SEARCH_TEXT_FIELD_MASK
                    )
                    .body(request)
                    .retrieve()
                    .body(
                        GoogleNearbySearchResponse.class
                    );

            if (
                response == null
                    || response.places() == null
            ) {
                return List.of();
            }

            return response
                .places()
                .stream()
                .map(this::toPlaceResponse)
                .toList();

        } catch (RestClientException exception) {
            throw toSearchException(exception);
        }
    }

    // 검색 요청(nearby, nearest, searchText)의 Google 오류는 모두 제공자 장애(503)로 본다.
    // 입력값은 서버에서 먼저 검증하므로 Google 400도 서버 요청 오류로 취급한다.
    private PlacesProviderException toSearchException(
        RestClientException exception
    ) {
        return new PlacesProviderException(exception);
    }

    // 상세 조회는 placeId가 사용자 입력이므로
    // Google 404는 404, Google 400은 400으로 돌려준다.
    // 단, 400이라도 오류 본문이 키·API 설정 문제를 가리키면 503이다.
    // 그 밖의 오류(5xx, 429, 401·403, 시간 초과, 응답 변환 실패)는 503이다.
    private BusinessException toPlaceDetailsException(
        RestClientException exception
    ) {
        if (exception instanceof RestClientResponseException responseException) {
            int status = responseException.getStatusCode().value();

            if (status == HttpStatus.NOT_FOUND.value()) {
                return new BusinessException(
                    ErrorCode.RESOURCE_NOT_FOUND,
                    "요청한 장소를 찾을 수 없습니다.",
                    exception
                );
            }

            if (status == HttpStatus.BAD_REQUEST.value()) {
                JsonNode error =
                    readGoogleError(responseException);

                if (isServerConfigError(error)) {
                    return new PlacesProviderException(exception);
                }

                // 오류 본문의 상태와 메시지만 남긴다(요청 헤더의 API 키는 남기지 않는다).
                log.warn(
                    "Google Place Details 400: status={}, message={}",
                    error.path("status").asString(""),
                    error.path("message").asString("")
                );

                return new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "장소 ID가 올바르지 않습니다.",
                    exception
                );
            }
        }

        return new PlacesProviderException(exception);
    }

    // Google 오류 본문의 error 객체를 읽는다.
    // 본문이 비었거나 JSON이 아니면 빈 노드를 돌려주어 일반 400으로 처리되게 한다.
    private JsonNode readGoogleError(
        RestClientResponseException exception
    ) {
        try {
            JsonNode root = JSON_MAPPER.readTree(
                exception.getResponseBodyAsString()
            );

            return root != null
                ? root.path("error")
                : JSON_MAPPER.missingNode();
        } catch (JacksonException parseException) {
            return JSON_MAPPER.missingNode();
        }
    }

    private boolean isServerConfigError(
        JsonNode error
    ) {
        for (JsonNode detail : error.path("details")) {
            String reason = detail.path("reason").asString("");

            if (SERVER_CONFIG_ERROR_REASONS.contains(reason)) {
                return true;
            }
        }

        return false;
    }

    private GoogleNearbySearchRequest createRequest(
        double latitude,
        double longitude,
        String type,
        int radius
    ) {
        var center =
            new GoogleNearbySearchRequest.Center(
                latitude,
                longitude
            );

        var circle =
            new GoogleNearbySearchRequest.Circle(
                center,
                radius
            );

        var restriction =
            new GoogleNearbySearchRequest
                .LocationRestriction(circle);

        return new GoogleNearbySearchRequest(
            List.of(type),
            20,
            restriction
        );
    }

    private double calculateDistance(
        double lat1,
        double lon1,
        double lat2,
        double lon2
    ) {
        final double earthRadius = 6371000;

        double latDistance =
            Math.toRadians(lat2 - lat1);

        double lonDistance =
            Math.toRadians(lon2 - lon1);

        double a =
            Math.sin(latDistance / 2)
                * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2)
                * Math.sin(lonDistance / 2);

        double c =
            2 * Math.atan2(
                Math.sqrt(a),
                Math.sqrt(1 - a)
            );

        return earthRadius * c;
    }

    private PlaceResponse toPlaceResponse(
        GoogleNearbySearchResponse.GooglePlace place
    ) {
        String name =
            place.displayName() != null
                ? place.displayName().text()
                : "이름 없는 장소";

        double latitude =
            place.location() != null
                ? place.location().latitude()
                : 0;

        double longitude =
            place.location() != null
                ? place.location().longitude()
                : 0;

        String address =
            place.formattedAddress() != null
                ? place.formattedAddress()
                : "주소 정보 없음";

        Boolean openNow =
            place.currentOpeningHours() != null
                ? place.currentOpeningHours().openNow()
                : null;

        return new PlaceResponse(
            place.id(),
            name,
            latitude,
            longitude,
            address,
            place.rating(),
            place.primaryType(),
            openNow
        );
    }
}