package com.tripbler.backend.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.tripbler.backend.auth.entity.RefreshToken;
import com.tripbler.backend.auth.exception.ExpiredRefreshTokenException;
import com.tripbler.backend.auth.exception.InvalidRefreshTokenException;
import com.tripbler.backend.auth.repository.RefreshTokenRepository;
import com.tripbler.backend.user.entity.User;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        refreshTokenService =
            new RefreshTokenService(
                refreshTokenRepository,
                14L
            );
    }

    @Test
    void createRefreshTokenWhenUserHasNoExistingToken() {

        User user =
            org.mockito.Mockito.mock(
                User.class
            );

        when(
            refreshTokenRepository.findByUser(user)
        ).thenReturn(
            Optional.empty()
        );

        String token =
            refreshTokenService.createOrUpdate(
                user
            );

        ArgumentCaptor<RefreshToken> captor =
            ArgumentCaptor.forClass(
                RefreshToken.class
            );

        verify(
            refreshTokenRepository
        ).save(
            captor.capture()
        );

        RefreshToken savedToken =
            captor.getValue();

        assertSame(
            user,
            savedToken.getUser()
        );

        assertEquals(
            token,
            savedToken.getToken()
        );
    }

    @Test
    void validateSuccess() {

        RefreshToken refreshToken =
            org.mockito.Mockito.mock(
                RefreshToken.class
            );

        when(
            refreshTokenRepository.findByToken(
                "valid-refresh-token"
            )
        ).thenReturn(
            Optional.of(refreshToken)
        );

        when(
            refreshToken.isExpired()
        ).thenReturn(false);

        RefreshToken result =
            refreshTokenService.validate(
                "valid-refresh-token"
            );

        assertSame(
            refreshToken,
            result
        );
    }

    @Test
    void validateFailsWhenTokenDoesNotExist() {

        when(
            refreshTokenRepository.findByToken(
                "invalid-refresh-token"
            )
        ).thenReturn(
            Optional.empty()
        );

        assertThrows(
            InvalidRefreshTokenException.class,
            () ->
                refreshTokenService.validate(
                    "invalid-refresh-token"
                )
        );
    }

    @Test
    void deleteByUserSuccess() {

        User user =
            org.mockito.Mockito.mock(
                User.class
            );

        refreshTokenService.deleteByUser(
            user
        );

        verify(
            refreshTokenRepository
        ).deleteByUser(
            user
        );
    }

    @Test
    void validateFailsWhenTokenIsExpired() {

        RefreshToken refreshToken =
            org.mockito.Mockito.mock(
                RefreshToken.class
            );

        when(
            refreshTokenRepository.findByToken(
                "expired-refresh-token"
            )
        ).thenReturn(
            Optional.of(refreshToken)
        );

        when(
            refreshToken.isExpired()
        ).thenReturn(true);

        assertThrows(
            ExpiredRefreshTokenException.class,
            () ->
                refreshTokenService.validate(
                    "expired-refresh-token"
                )
        );
    }
}