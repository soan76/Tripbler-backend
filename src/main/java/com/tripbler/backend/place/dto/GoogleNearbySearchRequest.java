package com.tripbler.backend.place.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GoogleNearbySearchRequest(
    List<String> includedTypes,
    int maxResultCount,
    LocationRestriction locationRestriction
) {

    public record LocationRestriction(
        Circle circle
    ) {
    }

    public record Circle(
        Center center,
        double radius
    ) {
    }

    public record Center(
        double latitude,
        double longitude
    ) {
    }
}