package com.tripbler.backend.user.controller;

import com.tripbler.backend.user.dto.UserCreateRequest;
import com.tripbler.backend.user.dto.UserResponse;
import com.tripbler.backend.user.service.UserService;
import com.tripbler.backend.user.dto.UserUpdateRequest;
import com.tripbler.backend.user.dto.UserPasswordChangeRequest;

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
import org.springframework.web.bind.annotation.PatchMapping;

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

        return userService.getUserById(userId);
    }

    @PatchMapping("/me")
    public UserResponse updateCurrentUser(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody UserUpdateRequest request
    ) {
        Long userId = Long.valueOf(
            jwt.getSubject()
        );

        return userService.updateUser(
            userId,
            request
        );
    }

    @PatchMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody UserPasswordChangeRequest request
    ) {
        Long userId = Long.valueOf(
            jwt.getSubject()
        );

        userService.changePassword(
            userId,
            request
        );
    }
}