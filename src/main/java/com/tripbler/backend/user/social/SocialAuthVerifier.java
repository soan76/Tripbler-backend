package com.tripbler.backend.user.social;

import com.tripbler.backend.user.entity.SocialProvider;

// 외부 플랫폼 토큰을 검증하고 인증된 사용자 정보를 반환한다.
public interface SocialAuthVerifier {

    SocialProvider getProvider();

    SocialUserInfo verify(String token);
}