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
}