package com.tripbler.backend.user.service;

import com.tripbler.backend.user.entity.SocialProvider;
import com.tripbler.backend.user.social.GoogleSocialAuthVerifier;
import com.tripbler.backend.user.social.SocialUserInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SocialAccountLinkServiceTest {

    @Mock
    private GoogleSocialAuthVerifier googleSocialAuthVerifier;

    @Mock
    private SocialAccountService socialAccountService;

    private SocialAccountLinkService socialAccountLinkService;

    @BeforeEach
    void setUp() {
        socialAccountLinkService = new SocialAccountLinkService(
            googleSocialAuthVerifier,
            socialAccountService
        );
    }

    @Test
    void linkGoogleAccountSucceeds() {
        Long userId = 1L;
        String idToken = "google-id-token";

        SocialUserInfo socialUserInfo = new SocialUserInfo(
            SocialProvider.GOOGLE,
            "google-user-123",
            "test@gmail.com"
        );

        when(googleSocialAuthVerifier.verify(idToken))
            .thenReturn(socialUserInfo);

        socialAccountLinkService.linkGoogleAccount(
            userId,
            idToken
        );

        verify(googleSocialAuthVerifier)
            .verify(idToken);

        verify(socialAccountService)
            .linkSocialAccount(
                userId,
                SocialProvider.GOOGLE,
                "google-user-123",
                "test@gmail.com"
            );
    }
}