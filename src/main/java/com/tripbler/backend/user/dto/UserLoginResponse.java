package com.tripbler.backend.user.dto;

import com.tripbler.backend.user.entity.User;

public record UserLoginResponse(
    Long id,
    String email,
    String accessToken,
    String refreshToken,
    String tokenType
) {

    public static UserLoginResponse of(
        User user,
        String accessToken,
        String refreshToken
    ) {
        return new UserLoginResponse(
            user.getId(),
            user.getEmail(),
            accessToken,
            refreshToken,
            "Bearer"
        );
    }
}