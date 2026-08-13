package com.tripbler.backend.place.client;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.tripbler.backend.common.exception.PlacesProviderException;
import com.tripbler.backend.place.dto.GoogleNearbySearchRequest;
import com.tripbler.backend.place.dto.GoogleNearbySearchResponse;
import com.tripbler.backend.place.dto.PlaceResponse;

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

    private final RestClient restClient;
    private final String apiKey;

    public GooglePlacesClient(
        RestClient.Builder restClientBuilder,
        @Value("${google.places.api-key}") String apiKey
    ) {
        this.restClient = restClientBuilder
            .baseUrl(GOOGLE_PLACES_BASE_URL)
            .build();

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
            throw new PlacesProviderException(
                exception
            );
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
            throw new PlacesProviderException(
                exception
            );
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

        } catch (PlacesProviderException exception) {
            throw exception;

        } catch (RestClientException exception) {
            throw new PlacesProviderException(
                exception
            );
        }
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