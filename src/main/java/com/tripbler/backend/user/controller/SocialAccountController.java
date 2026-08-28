package com.tripbler.backend.user.controller;

import com.tripbler.backend.user.dto.request.GoogleAccountLinkRequest;
import com.tripbler.backend.user.service.SocialAccountLinkService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/me/social-accounts")
public class SocialAccountController {

    private final SocialAccountLinkService socialAccountLinkService;

    public SocialAccountController(
        SocialAccountLinkService socialAccountLinkService
    ) {
        this.socialAccountLinkService = socialAccountLinkService;
    }

    // 현재 로그인한 Tripbler 사용자에게 Google 계정을 연동한다.
    @PostMapping("/google")
    public ResponseEntity<Void> linkGoogleAccount(
        Authentication authentication,
        @Valid @RequestBody GoogleAccountLinkRequest request
    ) {
        Long userId = Long.valueOf(
            authentication.getName()
        );

        socialAccountLinkService.linkGoogleAccount(
            userId,
            request.idToken()
        );

        return ResponseEntity.noContent().build();
    }
}