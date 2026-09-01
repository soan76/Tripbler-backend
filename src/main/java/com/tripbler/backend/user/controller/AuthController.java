package com.tripbler.backend.user.controller;

import com.tripbler.backend.auth.dto.request.FindIdSendCodeRequest;
import com.tripbler.backend.auth.dto.request.FindIdVerifyCodeRequest;
import com.tripbler.backend.auth.dto.response.FindIdVerifyCodeResponse;
import com.tripbler.backend.auth.service.FindIdVerificationService;
import com.tripbler.backend.user.dto.UserLoginRequest;
import com.tripbler.backend.user.dto.UserLoginResponse;
import com.tripbler.backend.user.service.AuthService;
import com.tripbler.backend.user.dto.TokenRefreshRequest;
import com.tripbler.backend.user.dto.TokenRefreshResponse;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final FindIdVerificationService findIdVerificationService;
    
    public AuthController(
        AuthService authService,
        FindIdVerificationService findIdVerificationService) {
        this.authService = authService;
        this.findIdVerificationService = findIdVerificationService;
    }

    @PostMapping("/login")
    public UserLoginResponse login(
        @Valid @RequestBody UserLoginRequest request
    ) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public TokenRefreshResponse refresh(
        @Valid @RequestBody TokenRefreshRequest request
    ) {
        return authService.refresh(request);
    }

    // 아이디 찾기용 이메일 인증코드를 발송한다.
    @PostMapping("/find-id/send-code")
    public ResponseEntity<Void> sendFindIdVerificationCode(
        @Valid @RequestBody FindIdSendCodeRequest request
    ) {
        findIdVerificationService.sendVerificationCode(
            request.email()
        );

        return ResponseEntity.noContent().build();
    }

    // 아이디 찾기 인증코드를 검증하고 Tripbler 아이디를 반환한다.
    @PostMapping("/find-id/verify-code")
    public FindIdVerifyCodeResponse verifyFindIdCode(
        @Valid @RequestBody FindIdVerifyCodeRequest request
    ) {
        String loginId = findIdVerificationService.verifyCode(
            request.email(),
            request.code()
        );

        return new FindIdVerifyCodeResponse(loginId);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
        @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = Long.valueOf(
            jwt.getSubject()
        );

        authService.logout(userId);
    }
}