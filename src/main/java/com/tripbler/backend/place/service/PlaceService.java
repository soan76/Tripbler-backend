package com.tripbler.backend.place.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.tripbler.backend.place.client.PlaceClient;
import com.tripbler.backend.place.dto.PlaceResponse;

@Service
public class PlaceService {

    private final PlaceClient placeClient;

    public PlaceService(
        PlaceClient placeClient
    ) {
        this.placeClient = placeClient;
    }

    public List<PlaceResponse> searchNearby(
        double latitude,
        double longitude,
        String type,
        int radius
    ) {
        return placeClient.searchNearby(
            latitude,
            longitude,
            type,
            radius
        );
    }

    public Optional<PlaceResponse> findNearestPlace(
        double latitude,
        double longitude,
        int radius
    ) {
        PlaceResponse response = placeClient.findNearestPlace(
                latitude,
                longitude,
                radius
        );

        return Optional.ofNullable(response);
    }

    public PlaceResponse getPlaceDetails(
        String placeId
    ) {
        return placeClient.getPlaceDetails(placeId);
    }
}