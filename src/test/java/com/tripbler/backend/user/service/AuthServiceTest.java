package com.tripbler.backend.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.tripbler.backend.auth.entity.RefreshToken;
import com.tripbler.backend.auth.service.JwtTokenService;
import com.tripbler.backend.auth.service.RefreshTokenService;
import com.tripbler.backend.user.dto.TokenRefreshRequest;
import com.tripbler.backend.user.dto.TokenRefreshResponse;
import com.tripbler.backend.user.dto.UserLoginRequest;
import com.tripbler.backend.user.dto.UserLoginResponse;
import com.tripbler.backend.user.entity.User;
import com.tripbler.backend.user.entity.UserRole;
import com.tripbler.backend.user.exception.InvalidCredentialsException;
import com.tripbler.backend.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private RefreshTokenService refreshTokenService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
            userRepository,
            passwordEncoder,
            jwtTokenService,
            refreshTokenService
        );
    }

    @Test
    void loginSuccess() {

        User user = org.mockito.Mockito.mock(User.class);

        when(user.getId())
            .thenReturn(1L);

        when(user.getEmail())
            .thenReturn("test@tripbler.com");

        when(user.getPassword())
            .thenReturn("encoded-password");

        when(user.getRole())
            .thenReturn(UserRole.USER);

        when(
            userRepository.findByLoginId(
                "testuser"
            )
        ).thenReturn(
            Optional.of(user)
        );

        when(
            passwordEncoder.matches(
                "password123",
                "encoded-password"
            )
        ).thenReturn(true);

        when(
            jwtTokenService.createAccessToken(
                1L,
                "test@tripbler.com",
                UserRole.USER
            )
        ).thenReturn(
            "access-token"
        );

        when(
            refreshTokenService.createOrUpdate(user)
        ).thenReturn(
            "refresh-token"
        );

        UserLoginRequest request =
            new UserLoginRequest(
                "testuser",
                "password123"
            );

        UserLoginResponse response =
            authService.login(request);

        assertEquals(
            1L,
            response.id()
        );

        assertEquals(
            "test@tripbler.com",
            response.email()
        );

        assertEquals(
            "access-token",
            response.accessToken()
        );

        assertEquals(
            "refresh-token",
            response.refreshToken()
        );

        assertEquals(
            "Bearer",
            response.tokenType()
        );
    }

    @Test
    void loginFailsWhenLoginIdDoesNotExist() {

        when(
            userRepository.findByLoginId(
                "missing-user"
            )
        ).thenReturn(
            Optional.empty()
        );

        UserLoginRequest request =
            new UserLoginRequest(
                "missing-user",
                "password123"
            );

        assertThrows(
            InvalidCredentialsException.class,
            () -> authService.login(request)
        );

        verify(
            refreshTokenService,
            never()
        ).createOrUpdate(any());
    }

    @Test
    void loginFailsWhenPasswordIsWrong() {

        User user = org.mockito.Mockito.mock(User.class);

        when(user.getPassword())
            .thenReturn("encoded-password");

        when(
            userRepository.findByLoginId(
                "testuser"
            )
        ).thenReturn(
            Optional.of(user)
        );

        when(
            passwordEncoder.matches(
                "wrong-password",
                "encoded-password"
            )
        ).thenReturn(false);

        UserLoginRequest request =
            new UserLoginRequest(
                "testuser",
                "wrong-password"
            );

        assertThrows(
            InvalidCredentialsException.class,
            () -> authService.login(request)
        );

        verify(
            jwtTokenService,
            never()
        ).createAccessToken(
            any(),
            any(),
            any()
        );

        verify(
            refreshTokenService,
            never()
        ).createOrUpdate(any());
    }

    @Test
    void refreshSuccess() {

        User user = org.mockito.Mockito.mock(User.class);

        when(user.getId())
            .thenReturn(1L);

        when(user.getEmail())
            .thenReturn("test@tripbler.com");

        when(user.getRole())
            .thenReturn(UserRole.USER);

        RefreshToken refreshToken =
            org.mockito.Mockito.mock(
                RefreshToken.class
            );

        when(refreshToken.getUser())
            .thenReturn(user);

        when(
            refreshTokenService.validate(
                "refresh-token"
            )
        ).thenReturn(
            refreshToken
        );

        when(
            jwtTokenService.createAccessToken(
                1L,
                "test@tripbler.com",
                UserRole.USER
            )
        ).thenReturn(
            "new-access-token"
        );

        TokenRefreshRequest request =
            new TokenRefreshRequest(
                "refresh-token"
            );

        TokenRefreshResponse response =
            authService.refresh(request);

        assertEquals(
            "new-access-token",
            response.accessToken()
        );

        assertEquals(
            "Bearer",
            response.tokenType()
        );
    }

    @Test
    void logoutSuccess() {

        User user = org.mockito.Mockito.mock(User.class);

        when(
            userRepository.findById(1L)
        ).thenReturn(
            Optional.of(user)
        );

        authService.logout(1L);

        verify(
            refreshTokenService
        ).deleteByUser(user);
    }
}