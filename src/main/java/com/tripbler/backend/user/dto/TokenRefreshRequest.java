package com.tripbler.backend.user.dto;

import jakarta.validation.constraints.NotBlank;

public record TokenRefreshRequest(

    @NotBlank(message = "Refresh Token을 입력해 주세요.")
    String refreshToken

) {
}