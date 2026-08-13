package com.tripbler.backend.place.dto;

public record PlaceResponse(
    String id,
    String name,
    double latitude,
    double longitude,
    String address,
    Double rating,
    String category,
    Boolean openNow
) {
}