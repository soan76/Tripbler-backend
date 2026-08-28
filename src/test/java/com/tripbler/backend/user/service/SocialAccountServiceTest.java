package com.tripbler.backend.user.service;

import com.tripbler.backend.common.exception.BusinessException;
import com.tripbler.backend.user.entity.SocialAccount;
import com.tripbler.backend.user.entity.SocialProvider;
import com.tripbler.backend.user.entity.User;
import com.tripbler.backend.user.exception.SocialAccountAlreadyLinkedException;
import com.tripbler.backend.user.exception.SocialAccountUsedByAnotherUserException;
import com.tripbler.backend.user.repository.SocialAccountRepository;
import com.tripbler.backend.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SocialAccountServiceTest {

    @Mock
    private SocialAccountRepository socialAccountRepository;

    @Mock
    private UserRepository userRepository;

    private SocialAccountService socialAccountService;

    @BeforeEach
    void setUp() {
        socialAccountService = new SocialAccountService(
            socialAccountRepository,
            userRepository
        );
    }

    @Test
    void linkSocialAccountSucceeds() {
        // 정상 사용자가 아직 연동하지 않은 Google 계정을 연결한다.
        User user = new User(
            "testuser",
            "테스트사용자",
            "encoded-password"
        );

        when(userRepository.findById(1L))
            .thenReturn(Optional.of(user));

        when(
            socialAccountRepository.existsByUserIdAndProvider(
                1L,
                SocialProvider.GOOGLE
            )
        ).thenReturn(false);

        when(
            socialAccountRepository.existsByProviderAndProviderUserId(
                SocialProvider.GOOGLE,
                "google-user-123"
            )
        ).thenReturn(false);

        when(socialAccountRepository.save(any(SocialAccount.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        SocialAccount result =
            socialAccountService.linkSocialAccount(
                1L,
                SocialProvider.GOOGLE,
                "google-user-123"
            );

        assertEquals(
            SocialProvider.GOOGLE,
            result.getProvider()
        );

        assertEquals(
            "google-user-123",
            result.getProviderUserId()
        );

        verify(socialAccountRepository)
            .save(any(SocialAccount.class));
    }

    @Test
    void linkFailsWhenProviderAlreadyLinked() {
        // 사용자가 같은 플랫폼을 이미 연동한 경우 중복 연동을 차단한다.
        User user = new User(
            "testuser",
            "테스트사용자",
            "encoded-password"
        );

        when(userRepository.findById(1L))
            .thenReturn(Optional.of(user));

        when(
            socialAccountRepository.existsByUserIdAndProvider(
                1L,
                SocialProvider.GOOGLE
            )
        ).thenReturn(true);

        assertThrows(
            SocialAccountAlreadyLinkedException.class,
            () -> socialAccountService.linkSocialAccount(
                1L,
                SocialProvider.GOOGLE,
                "google-user-123"
            )
        );

        verify(
            socialAccountRepository,
            never()
        ).save(any());
    }

    @Test
    void linkFailsWhenSocialAccountUsedByAnotherUser() {
        // 동일한 플랫폼 계정이 다른 사용자에게 연결돼 있으면 연동을 차단한다.
        User user = new User(
            "testuser",
            "테스트사용자",
            "encoded-password"
        );

        when(userRepository.findById(1L))
            .thenReturn(Optional.of(user));

        when(
            socialAccountRepository.existsByUserIdAndProvider(
                1L,
                SocialProvider.GOOGLE
            )
        ).thenReturn(false);

        when(
            socialAccountRepository.existsByProviderAndProviderUserId(
                SocialProvider.GOOGLE,
                "google-user-123"
            )
        ).thenReturn(true);

        assertThrows(
            SocialAccountUsedByAnotherUserException.class,
            () -> socialAccountService.linkSocialAccount(
                1L,
                SocialProvider.GOOGLE,
                "google-user-123"
            )
        );

        verify(
            socialAccountRepository,
            never()
        ).save(any());
    }

    @Test
    void linkFailsWhenUserNotFound() {
        // 존재하지 않는 사용자 ID로 연동을 시도하면 실패한다.
        when(userRepository.findById(999L))
            .thenReturn(Optional.empty());

        assertThrows(
            BusinessException.class,
            () -> socialAccountService.linkSocialAccount(
                999L,
                SocialProvider.GOOGLE,
                "google-user-123"
            )
        );

        verifyNoInteractions(
            socialAccountRepository
        );
    }
}