package com.tripbler.backend.auth.dto.response;

public record PasswordResetVerifyCodeResponse(
    String resetToken
) {
}