package com.tripbler.backend.place.client;

import java.util.List;

import com.tripbler.backend.place.dto.PlaceResponse;

public interface PlaceClient {

    List<PlaceResponse> searchNearby(
        double latitude,
        double longitude,
        String type,
        int radius
    );

    PlaceResponse findNearestPlace(
        double latitude,
        double longitude,
        int radius
    );

    PlaceResponse getPlaceDetails(String placeId);

    // 좌표(latitude, longitude)는 둘 다 있거나 둘 다 null이다.
    // 좌표가 있으면 그 주변 결과를 우선하고, 없으면 위치 기준 없이 검색한다.
    List<PlaceResponse> searchText(
        String query,
        Double latitude,
        Double longitude
    );
}