package com.tripbler.backend.user.social;

import com.tripbler.backend.user.entity.SocialProvider;
import com.tripbler.backend.user.exception.InvalidSocialTokenException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GoogleSocialAuthVerifierTest {

    private GoogleSocialAuthVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new GoogleSocialAuthVerifier(
            "test-client-id.apps.googleusercontent.com"
        );
    }

    @Test
    void providerIsGoogle() {
        assertEquals(
            SocialProvider.GOOGLE,
            verifier.getProvider()
        );
    }

    @Test
    void verifyFailsWhenTokenIsNull() {
        // 토큰이 없으면 Google 인증을 진행하지 않는다.
        assertThrows(
            InvalidSocialTokenException.class,
            () -> verifier.verify(null)
        );
    }

    @Test
    void verifyFailsWhenTokenIsBlank() {
        // 빈 토큰도 유효한 인증 정보로 인정하지 않는다.
        assertThrows(
            InvalidSocialTokenException.class,
            () -> verifier.verify("   ")
        );
    }
}