package com.tripbler.backend.place.dto;

public record PlaceResponse(
    String id,
    String name,
    // Google 응답에 위치가 없으면 둘 다 null이다.
    Double latitude,
    Double longitude,
    String address,
    Double rating,
    String category,
    Boolean openNow
) {
}