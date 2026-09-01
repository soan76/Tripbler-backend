package com.tripbler.backend.auth.service;

import com.tripbler.backend.auth.entity.PasswordResetToken;
import com.tripbler.backend.auth.repository.PasswordResetTokenRepository;
import com.tripbler.backend.common.exception.BusinessException;
import com.tripbler.backend.common.exception.ErrorCode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetTokenServiceTest {

    @Mock
    private PasswordResetTokenRepository tokenRepository;

    private PasswordResetTokenService service;

    @BeforeEach
    void setUp() {
        service = new PasswordResetTokenService(
            tokenRepository
        );
    }

    @Test
    void createResetTokenSucceeds() {
        Long userId = 1L;

        when(
            tokenRepository.save(
                any(PasswordResetToken.class)
            )
        ).thenAnswer(
            invocation ->
                invocation.getArgument(0)
        );

        Instant before = Instant.now();

        String rawToken =
            service.createResetToken(userId);

        Instant after = Instant.now();

        assertNotNull(rawToken);
        assertFalse(rawToken.isBlank());

        verify(tokenRepository)
            .deleteAllByUserId(userId);

        ArgumentCaptor<PasswordResetToken> captor =
            ArgumentCaptor.forClass(
                PasswordResetToken.class
            );

        verify(tokenRepository)
            .save(captor.capture());

        PasswordResetToken savedToken =
            captor.getValue();

        assertEquals(
            userId,
            savedToken.getUserId()
        );

        assertFalse(savedToken.isUsed());

        assertEquals(
            sha256(rawToken),
            savedToken.getTokenHash()
        );

        assertFalse(
            savedToken.getExpiresAt().isBefore(
                before.plus(
                    10,
                    ChronoUnit.MINUTES
                )
            )
        );

        assertFalse(
            savedToken.getExpiresAt().isAfter(
                after.plus(
                    10,
                    ChronoUnit.MINUTES
                )
            )
        );
    }

    @Test
    void validateResetTokenSucceeds() {
        String rawToken = "valid-reset-token";
        String tokenHash = sha256(rawToken);

        PasswordResetToken token =
            new PasswordResetToken(
                1L,
                tokenHash,
                Instant.now().plus(
                    10,
                    ChronoUnit.MINUTES
                )
            );

        when(
            tokenRepository.findByTokenHash(tokenHash)
        ).thenReturn(Optional.of(token));

        Long userId =
            service.validateResetToken(rawToken);

        assertEquals(
            1L,
            userId
        );
    }

    @Test
    void validateResetTokenFailsWhenTokenDoesNotExist() {
        String rawToken = "invalid-reset-token";
        String tokenHash = sha256(rawToken);

        when(
            tokenRepository.findByTokenHash(tokenHash)
        ).thenReturn(Optional.empty());

        BusinessException exception =
            assertThrows(
                BusinessException.class,
                () ->
                    service.validateResetToken(
                        rawToken
                    )
            );

        assertEquals(
            ErrorCode.INVALID_PASSWORD_RESET_TOKEN,
            exception.getErrorCode()
        );
    }

    @Test
    void validateResetTokenFailsWhenExpired() {
        String rawToken = "expired-reset-token";
        String tokenHash = sha256(rawToken);

        PasswordResetToken token =
            new PasswordResetToken(
                1L,
                tokenHash,
                Instant.now().minus(
                    1,
                    ChronoUnit.MINUTES
                )
            );

        when(
            tokenRepository.findByTokenHash(tokenHash)
        ).thenReturn(Optional.of(token));

        BusinessException exception =
            assertThrows(
                BusinessException.class,
                () ->
                    service.validateResetToken(
                        rawToken
                    )
            );

        assertEquals(
            ErrorCode.EXPIRED_PASSWORD_RESET_TOKEN,
            exception.getErrorCode()
        );
    }

    @Test
    void validateResetTokenFailsWhenAlreadyUsed() {
        String rawToken = "used-reset-token";
        String tokenHash = sha256(rawToken);

        PasswordResetToken token =
            new PasswordResetToken(
                1L,
                tokenHash,
                Instant.now().plus(
                    10,
                    ChronoUnit.MINUTES
                )
            );

        token.markUsed();

        when(
            tokenRepository.findByTokenHash(tokenHash)
        ).thenReturn(Optional.of(token));

        BusinessException exception =
            assertThrows(
                BusinessException.class,
                () ->
                    service.validateResetToken(
                        rawToken
                    )
            );

        assertEquals(
            ErrorCode.INVALID_PASSWORD_RESET_TOKEN,
            exception.getErrorCode()
        );
    }

    @Test
    void markTokenUsedSucceeds() {
        String rawToken = "valid-reset-token";
        String tokenHash = sha256(rawToken);

        PasswordResetToken token =
            new PasswordResetToken(
                1L,
                tokenHash,
                Instant.now().plus(
                    10,
                    ChronoUnit.MINUTES
                )
            );

        when(
            tokenRepository.findByTokenHash(tokenHash)
        ).thenReturn(Optional.of(token));

        service.markTokenUsed(rawToken);

        assertTrue(token.isUsed());
    }

    private String sha256(String value) {
        try {
            MessageDigest digest =
                MessageDigest.getInstance("SHA-256");

            byte[] hash =
                digest.digest(
                    value.getBytes(
                        StandardCharsets.UTF_8
                    )
                );

            return HexFormat.of()
                .formatHex(hash);

        } catch (Exception exception) {
            throw new IllegalStateException(
                exception
            );
        }
    }
}