package com.tripbler.backend.user.dto.response;

import com.tripbler.backend.user.entity.SocialProvider;

import java.util.List;

public record SocialAccountStatusResponse(
    // 현재 사용자에게 연동된 외부 플랫폼 목록
    List<SocialProvider> linkedProviders
) {
}