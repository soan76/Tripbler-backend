package com.tripbler.backend.user.controller;

import com.tripbler.backend.auth.dto.request.FindIdSendCodeRequest;
import com.tripbler.backend.auth.dto.request.FindIdVerifyCodeRequest;
import com.tripbler.backend.auth.dto.response.FindIdVerifyCodeResponse;
import com.tripbler.backend.auth.dto.request.PasswordResetSendCodeRequest;
import com.tripbler.backend.auth.dto.request.PasswordResetVerifyCodeRequest;
import com.tripbler.backend.auth.dto.request.PasswordResetRequest;
import com.tripbler.backend.auth.dto.response.PasswordResetVerifyCodeResponse;
import com.tripbler.backend.auth.service.FindIdVerificationService;
import com.tripbler.backend.auth.service.PasswordResetVerificationService;
import com.tripbler.backend.auth.service.PasswordResetService;

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
    private final PasswordResetVerificationService passwordResetVerificationService;
    private final PasswordResetService passwordResetService;
    
    public AuthController(
        AuthService authService,
        FindIdVerificationService findIdVerificationService,
        PasswordResetVerificationService passwordResetVerificationService,
        PasswordResetService passwordResetService
    ) {
        this.authService = authService;
        this.findIdVerificationService = findIdVerificationService;
        this.passwordResetVerificationService = passwordResetVerificationService;
        this.passwordResetService = passwordResetService;
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

    // 비밀번호 재설정용 인증코드를 발송한다.
    @PostMapping("/password-reset/send-code")
    public ResponseEntity<Void> sendPasswordResetVerificationCode(
        @Valid @RequestBody PasswordResetSendCodeRequest request
    ) {
        passwordResetVerificationService.sendVerificationCode(
            request.loginId(),
            request.email()
        );

        return ResponseEntity.noContent().build();
    }

    // 비밀번호 재설정 인증코드를 검증하고 resetToken을 반환한다.
    @PostMapping("/password-reset/verify-code")
    public PasswordResetVerifyCodeResponse verifyPasswordResetCode(
        @Valid @RequestBody PasswordResetVerifyCodeRequest request
    ) {
        String resetToken =
            passwordResetVerificationService.verifyCode(
                request.loginId(),
                request.email(),
                request.code()
            );

        return new PasswordResetVerifyCodeResponse(
            resetToken
        );
    }

    // 본인인증 후 발급된 resetToken으로 비밀번호를 재설정한다.
    @PostMapping("/password-reset")
    public ResponseEntity<Void> resetPassword(
        @Valid @RequestBody PasswordResetRequest request
    ) {
        passwordResetService.resetPassword(
            request.resetToken(),
            request.newPassword()
        );

        return ResponseEntity.noContent().build();
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