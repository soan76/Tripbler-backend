package com.tripbler.backend.user.controller;

import com.tripbler.backend.user.dto.request.GoogleAccountLinkRequest;
import com.tripbler.backend.user.dto.response.SocialAccountStatusResponse;
import com.tripbler.backend.user.entity.SocialProvider;
import com.tripbler.backend.user.service.SocialAccountLinkService;
import com.tripbler.backend.user.service.SocialAccountService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/me/social-accounts")
public class SocialAccountController {

    private final SocialAccountLinkService socialAccountLinkService;
    private final SocialAccountService socialAccountService;

    public SocialAccountController(
        SocialAccountLinkService socialAccountLinkService,
        SocialAccountService socialAccountService
    ) {
        this.socialAccountLinkService = socialAccountLinkService;
        this.socialAccountService = socialAccountService;
    }

    // 현재 사용자에게 연동된 소셜 계정 목록을 조회한다.
    @GetMapping
    public ResponseEntity<SocialAccountStatusResponse> getLinkedSocialAccounts(
        Authentication authentication
    ) {
        Long userId = Long.valueOf(authentication.getName());

        SocialAccountStatusResponse response =
            socialAccountService.getLinkedSocialAccounts(userId);

        return ResponseEntity.ok(response);
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

    // 현재 사용자에게 연동된 Google 계정을 해제한다.
    @DeleteMapping("/google")
    public ResponseEntity<Void> unlinkGoogleAccount(
        Authentication authentication
    ) {
        Long userId = Long.valueOf(
            authentication.getName()
        );

        socialAccountService.unlinkSocialAccount(
            userId,
            SocialProvider.GOOGLE
        );

        return ResponseEntity.noContent().build();
    }
}