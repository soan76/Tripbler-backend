package com.tripbler.backend.user.social;

import com.tripbler.backend.user.entity.SocialProvider;

// 외부 플랫폼 인증이 완료된 사용자의 식별 정보를 전달한다.
public record SocialUserInfo(
    SocialProvider provider,
    String providerUserId,
    String providerEmail
) {
}