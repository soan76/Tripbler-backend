package com.tripbler.backend.place.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tripbler.backend.place.dto.PlaceResponse;
import com.tripbler.backend.place.service.PlaceService;

@RestController
@RequestMapping("/api/v1/places")
public class PlaceController {

    private final PlaceService placeService;

    public PlaceController(
        PlaceService placeService
    ) {
        this.placeService = placeService;
    }

    @GetMapping("/nearby")
    public List<PlaceResponse> searchNearby(
        @RequestParam("lat")
        double latitude,

        @RequestParam("lng")
        double longitude,

        @RequestParam(defaultValue = "tourist_attraction")
        String type,

        @RequestParam(defaultValue = "1500")
        int radius
    ) {
        return placeService.searchNearby(
            latitude,
            longitude,
            type,
            radius
        );
    }

    @GetMapping("/nearest")
    public ResponseEntity<PlaceResponse> getNearestPlace(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "40") int radius
    ) {
        return placeService.findNearestPlace(lat, lng, radius)
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