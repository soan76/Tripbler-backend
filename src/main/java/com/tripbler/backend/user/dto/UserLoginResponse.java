package com.tripbler.backend.user.dto;

import com.tripbler.backend.user.entity.User;

public record UserLoginResponse(
    Long id,
    String loginId,
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
            user.getLoginId(),
            accessToken,
            refreshToken,
            "Bearer"
        );
    }
}