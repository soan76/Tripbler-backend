package com.tripbler.backend.user.service;

import com.tripbler.backend.user.social.GoogleSocialAuthVerifier;
import com.tripbler.backend.user.social.SocialUserInfo;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SocialAccountLinkService {

    private final GoogleSocialAuthVerifier googleSocialAuthVerifier;
    private final SocialAccountService socialAccountService;

    public SocialAccountLinkService(
        GoogleSocialAuthVerifier googleSocialAuthVerifier,
        SocialAccountService socialAccountService
    ) {
        this.googleSocialAuthVerifier = googleSocialAuthVerifier;
        this.socialAccountService = socialAccountService;
    }

    // Google 토큰을 검증한 뒤 인증된 계정을 현재 Tripbler 사용자에게 연동한다.
    public void linkGoogleAccount(
        Long userId,
        String idToken
    ) {
        SocialUserInfo socialUserInfo =
            googleSocialAuthVerifier.verify(idToken);

        socialAccountService.linkSocialAccount(
            userId,
            socialUserInfo.provider(),
            socialUserInfo.providerUserId()
        );
    }
}