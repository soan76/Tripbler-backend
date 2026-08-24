package com.tripbler.backend.auth.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripbler.backend.auth.entity.RefreshToken;
import com.tripbler.backend.auth.exception.ExpiredRefreshTokenException;
import com.tripbler.backend.auth.exception.InvalidRefreshTokenException;
import com.tripbler.backend.auth.repository.RefreshTokenRepository;
import com.tripbler.backend.user.entity.User;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    private final long refreshTokenExpirationDays;

    private final SecureRandom secureRandom =
        new SecureRandom();

    public RefreshTokenService(
        RefreshTokenRepository refreshTokenRepository,
        @Value("${security.refresh-token.expiration-days}")
        long refreshTokenExpirationDays
    ) {
        this.refreshTokenRepository =
            refreshTokenRepository;

        this.refreshTokenExpirationDays =
            refreshTokenExpirationDays;
    }

    @Transactional
    public String createOrUpdate(
        User user
    ) {
        String token = generateToken();

        LocalDateTime expiresAt =
            LocalDateTime.now()
                .plusDays(refreshTokenExpirationDays);

        RefreshToken refreshToken =
            refreshTokenRepository
                .findByUser(user)
                .orElseGet(() ->
                    new RefreshToken(
                        user,
                        token,
                        expiresAt
                    )
                );

        if (refreshToken.getId() != null) {
            refreshToken.update(
                token,
                expiresAt
            );
        }

        refreshTokenRepository.save(
            refreshToken
        );

        return token;
    }

    @Transactional(readOnly = true)
    public RefreshToken validate(
        String token
    ) {
        RefreshToken refreshToken =
            refreshTokenRepository
                .findByToken(token)
                .orElseThrow(
                    InvalidRefreshTokenException::new
                );

        if (refreshToken.isExpired()) {
            throw new ExpiredRefreshTokenException();
        }

        return refreshToken;
    }

    @Transactional
    public void deleteByUser(
        User user
    ) {
        refreshTokenRepository.deleteByUser(
            user
        );
    }

    private String generateToken() {

        byte[] bytes = new byte[32];

        secureRandom.nextBytes(bytes);

        return Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(bytes);
    }
}