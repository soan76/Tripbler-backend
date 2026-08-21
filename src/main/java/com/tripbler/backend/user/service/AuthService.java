package com.tripbler.backend.user.service;

import com.tripbler.backend.auth.service.JwtTokenService;
import com.tripbler.backend.user.dto.UserLoginRequest;
import com.tripbler.backend.user.dto.UserLoginResponse;
import com.tripbler.backend.user.entity.User;
import com.tripbler.backend.user.exception.InvalidCredentialsException;
import com.tripbler.backend.user.repository.UserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        JwtTokenService jwtTokenService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    @Transactional(readOnly = true)
    public UserLoginResponse login(
        UserLoginRequest request
    ) {
        User user = userRepository
            .findByEmail(request.email())
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
                user.getEmail(),
                user.getRole()
            );

        return UserLoginResponse.of(
            user,
            accessToken
        );
    }
}