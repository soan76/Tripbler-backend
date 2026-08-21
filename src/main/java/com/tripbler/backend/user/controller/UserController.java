package com.tripbler.backend.user.controller;

import com.tripbler.backend.user.dto.UserCreateRequest;
import com.tripbler.backend.user.dto.UserResponse;
import com.tripbler.backend.user.service.UserService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(
        @Valid @RequestBody UserCreateRequest request
    ) {
        return userService.createUser(request);
    }

    @GetMapping("/me")
    public UserResponse getCurrentUser(
        @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = Long.valueOf(
            jwt.getSubject()
        );

        String email = jwt.getClaimAsString(
            "email"
        );

        return new UserResponse(
            userId,
            email
        );
    }
}