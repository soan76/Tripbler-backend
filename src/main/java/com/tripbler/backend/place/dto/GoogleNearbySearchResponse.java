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

    // Google이 빈 location 객체나 한쪽 좌표만 보낼 수 있어 null을 허용한다.
    // 원시 double이면 역직렬화가 실패하거나 (0, 0)이 될 수 있다.
    public record Location(
        Double latitude,
        Double longitude
    ) {
    }

    public record OpeningHours(
        Boolean openNow
    ) {
    }
}