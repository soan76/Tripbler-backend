package com.tripbler.backend.user.dto;

public record LoginIdAvailabilityResponse(
    String loginId,
    boolean available
) {
}