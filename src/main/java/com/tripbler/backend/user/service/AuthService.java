package com.tripbler.backend.user.service;

import com.tripbler.backend.auth.entity.RefreshToken;
import com.tripbler.backend.auth.service.JwtTokenService;
import com.tripbler.backend.auth.service.RefreshTokenService;
import com.tripbler.backend.user.dto.TokenRefreshRequest;
import com.tripbler.backend.user.dto.TokenRefreshResponse;
import com.tripbler.backend.user.dto.UserLoginRequest;
import com.tripbler.backend.user.dto.UserLoginResponse;
import com.tripbler.backend.user.entity.User;
import com.tripbler.backend.user.exception.InvalidCredentialsException;
import com.tripbler.backend.user.exception.UserNotFoundException;
import com.tripbler.backend.user.repository.UserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        JwtTokenService jwtTokenService,
        RefreshTokenService refreshTokenService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public UserLoginResponse login(
        UserLoginRequest request
    ) {
        User user = userRepository
            .findByLoginId(request.loginId())
            .orElseThrow(
                InvalidCredentialsException::new
            );

        boolean passwordMatches =
            passwordEncoder.matches(
                request.password(),
                user.getPassword()
            );

        if (!passwordMatches) {
            throw new InvalidCredentialsException();
        }

        String accessToken =
            jwtTokenService.createAccessToken(
                user.getId(),
                user.getRole()
            );

        String refreshToken =
            refreshTokenService.createOrUpdate(
                user
            );

        return UserLoginResponse.of(
            user,
            accessToken,
            refreshToken
        );
    }

    @Transactional(readOnly = true)
    public TokenRefreshResponse refresh(
        TokenRefreshRequest request
    ) {
        RefreshToken refreshToken =
            refreshTokenService.validate(
                request.refreshToken()
            );

        User user =
            refreshToken.getUser();

        String accessToken =
            jwtTokenService.createAccessToken(
                user.getId(),
                user.getRole()
            );

        return TokenRefreshResponse.of(
            accessToken
        );
    }

    @Transactional
    public void logout(
        Long userId
    ) {
        User user = userRepository
            .findById(userId)
            .orElseThrow(
                UserNotFoundException::new
            );

        refreshTokenService.deleteByUser(
            user
        );
    }
}