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

    /**
     * 로그아웃: 요청한 Refresh Token과 일치하는 토큰만 원자적으로 폐기한다.
     *
     * 이미 폐기됐거나 같은 사용자의 재로그인으로 교체된 토큰이면 아무것도 지우지 않는다.
     * 그래서 늦게 도착한 이전 로그아웃이 새 로그인 세션의 토큰을 지우지 않는다.
     * 지웠으면 true(호출부는 결과와 관계없이 같은 응답을 돌려준다).
     */
    @Transactional
    public boolean revoke(
        String token
    ) {
        return refreshTokenRepository.deleteByToken(token) > 0;
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