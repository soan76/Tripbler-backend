package com.tripbler.backend.place.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

// Google Places Text Search(places:searchText) 요청 본문.
// 좌표가 없는 검색은 locationBias 필드 자체를 보내지 않는다("locationBias": null도 보내지 않는다).
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GoogleTextSearchRequest(
    String textQuery,
    String languageCode,
    int pageSize,
    LocationBias locationBias
) {

    public record LocationBias(
        GoogleNearbySearchRequest.Circle circle
    ) {
    }
}
