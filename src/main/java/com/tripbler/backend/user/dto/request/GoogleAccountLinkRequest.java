package com.tripbler.backend.user.dto.request;

import jakarta.validation.constraints.NotBlank;

public record GoogleAccountLinkRequest(

    // Flutter에서 Google 로그인 후 받은 ID Token을 전달한다.
    @NotBlank
    String idToken
) {
}