package com.tripbler.backend.user.social;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.tripbler.backend.user.entity.SocialProvider;
import com.tripbler.backend.user.exception.InvalidSocialTokenException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

@Component
public class GoogleSocialAuthVerifier
    implements SocialAuthVerifier {

    private final GoogleIdTokenVerifier verifier;

    public GoogleSocialAuthVerifier(
        @Value("${google.oauth.client-id}")
        String clientId
    ) {
        this.verifier =
            new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance()
            )
                .setAudience(List.of(clientId))
                .build();
    }

    @Override
    public SocialProvider getProvider() {
        return SocialProvider.GOOGLE;
    }

    // Google ID Token을 검증하고 Google 계정의 고유 식별자를 반환한다.
    @Override
    public SocialUserInfo verify(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidSocialTokenException();
        }

        try {
            GoogleIdToken idToken =
                verifier.verify(token);

            if (idToken == null) {
                throw new InvalidSocialTokenException();
            }

            String providerUserId =
                idToken.getPayload().getSubject();

            String providerEmail =
                idToken.getPayload().getEmail();

            Boolean emailVerified =
                idToken.getPayload().getEmailVerified();

            if (providerEmail == null ||
                providerEmail.isBlank() ||
                !Boolean.TRUE.equals(emailVerified)) {
                throw new InvalidSocialTokenException();
            }

            return new SocialUserInfo(
                SocialProvider.GOOGLE,
                providerUserId,
                providerEmail
            );
        } catch (
            GeneralSecurityException |
            IOException error
        ) {
            throw new InvalidSocialTokenException(
                error
            );
        }
    }
}