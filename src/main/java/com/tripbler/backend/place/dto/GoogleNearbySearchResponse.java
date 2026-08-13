package com.tripbler.backend.place.dto;

import java.util.List;

public record GoogleNearbySearchResponse(
    List<GooglePlace> places
) {

    public record GooglePlace(
        String id,
        LocalizedText displayName,
        Location location,
        String formattedAddress,
        Double rating,
        String primaryType,
        OpeningHours currentOpeningHours
    ) {
    }

    public record LocalizedText(
        String text,
        String languageCode
    ) {
    }

    public record Location(
        double latitude,
        double longitude
    ) {
    }

    public record OpeningHours(
        Boolean openNow
    ) {
    }
}