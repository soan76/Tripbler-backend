package com.tripbler.backend.user.controller;

import com.tripbler.backend.user.dto.LoginIdAvailabilityResponse;
import com.tripbler.backend.user.dto.UserCreateRequest;
import com.tripbler.backend.user.dto.UserPasswordChangeRequest;
import com.tripbler.backend.user.dto.UserResponse;
import com.tripbler.backend.user.service.UserService;
import com.tripbler.backend.auth.service.AccountDeletionService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private final AccountDeletionService accountDeletionService;

    public UserController(
        UserService userService,
        AccountDeletionService accountDeletionService
    ) {
        this.userService = userService;
        this.accountDeletionService = accountDeletionService;
    }
    // 새로운 사용자를 생성한다.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(
        @Valid @RequestBody UserCreateRequest request
    ) {
        return userService.createUser(request);
    }
    // 현재 로그인 사용자의 정보를 조회한다.
    @GetMapping("/me")
    public UserResponse getCurrentUser(
        @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = Long.valueOf(
            jwt.getSubject()
        );

        return userService.getUserById(userId);
    }
    // 로그인 ID 중복 여부를 확인한다. 
    @GetMapping("/check-login-id")
    public LoginIdAvailabilityResponse checkLoginIdAvailability(
        @RequestParam String loginId
    ) {
        return userService.checkLoginIdAvailability(
            loginId
        );
    }

    // 현재 로그인 사용자의 비밀번호를 변경한다.
    // 비밀번호 변경 요청 시, 현재 로그인 사용자의 ID를 JWT에서 추출하여 서비스에 전달한다.
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

    // 현재 로그인 사용자의 계정과 관련 데이터를 모두 삭제한다.
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCurrentUser(
        @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = Long.valueOf(
            jwt.getSubject()
        );

        accountDeletionService.deleteAccount(
            userId
        );
    }
}