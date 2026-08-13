package com.tripbler.backend.place.service;

import java.util.List;

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

    public PlaceResponse findNearestPlace(
        double latitude,
        double longitude
    ) {
        return placeClient.findNearestPlace(
            latitude,
            longitude
        );
    }
}