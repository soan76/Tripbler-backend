package com.tripbler.backend.auth.service;

import com.tripbler.backend.auth.entity.PasswordResetToken;
import com.tripbler.backend.auth.repository.PasswordResetTokenRepository;
import com.tripbler.backend.common.exception.BusinessException;
import com.tripbler.backend.common.exception.ErrorCode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;

@Service
@Transactional
public class PasswordResetTokenService {

    private static final int TOKEN_BYTE_LENGTH = 32;
    private static final long TOKEN_EXPIRATION_MINUTES = 10;

    private final PasswordResetTokenRepository tokenRepository;

    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetTokenService(
        PasswordResetTokenRepository tokenRepository
    ) {
        this.tokenRepository = tokenRepository;
    }

    // 본인인증이 완료된 사용자에게
    // 일회성 비밀번호 재설정 토큰을 발급한다.
    public String createResetToken(Long userId) {

        // 이전에 발급된 토큰이 있으면 제거한다.
        tokenRepository.deleteAllByUserId(userId);

        String rawToken = generateToken();
        String tokenHash = hashToken(rawToken);

        Instant expiresAt = Instant.now()
            .plus(
                TOKEN_EXPIRATION_MINUTES,
                ChronoUnit.MINUTES
            );

        PasswordResetToken resetToken =
            new PasswordResetToken(
                userId,
                tokenHash,
                expiresAt
            );

        tokenRepository.save(resetToken);

        // 원문은 DB에 저장하지 않고 Flutter에만 반환한다.
        return rawToken;
    }

    // 실제 비밀번호 변경 시 resetToken을 검증한다.
    public Long validateResetToken(String rawToken) {

        String tokenHash = hashToken(rawToken);

        PasswordResetToken resetToken =
            tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(
                    () -> new BusinessException(
                        ErrorCode.INVALID_PASSWORD_RESET_TOKEN
                    )
                );

        if (resetToken.isUsed()) {
            throw new BusinessException(
                ErrorCode.INVALID_PASSWORD_RESET_TOKEN
            );
        }

        if (resetToken.isExpired()) {
            throw new BusinessException(
                ErrorCode.EXPIRED_PASSWORD_RESET_TOKEN
            );
        }

        return resetToken.getUserId();
    }

    // 비밀번호 변경이 성공한 뒤 토큰을 사용 완료 처리한다.
    public void markTokenUsed(String rawToken) {

        String tokenHash = hashToken(rawToken);

        PasswordResetToken resetToken =
            tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(
                    () -> new BusinessException(
                        ErrorCode.INVALID_PASSWORD_RESET_TOKEN
                    )
                );

        if (resetToken.isUsed()) {
            throw new BusinessException(
                ErrorCode.INVALID_PASSWORD_RESET_TOKEN
            );
        }

        if (resetToken.isExpired()) {
            throw new BusinessException(
                ErrorCode.EXPIRED_PASSWORD_RESET_TOKEN
            );
        }

        resetToken.markUsed();
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];

        secureRandom.nextBytes(bytes);

        return Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest =
                MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                rawToken.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                "SHA-256 알고리즘을 사용할 수 없습니다.",
                exception
            );
        }
    }
}