package com.tripbler.backend.user.controller;

import com.tripbler.backend.user.dto.UserLoginRequest;
import com.tripbler.backend.user.dto.UserLoginResponse;
import com.tripbler.backend.user.service.AuthService;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public UserLoginResponse login(
        @Valid @RequestBody UserLoginRequest request
    ) {
        return authService.login(request);
    }
}