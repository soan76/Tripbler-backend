package com.tripbler.backend.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.tripbler.backend.auth.repository.FindIdVerificationRepository;
import com.tripbler.backend.auth.repository.PasswordResetTokenRepository;
import com.tripbler.backend.auth.repository.PasswordResetVerificationRepository;
import com.tripbler.backend.auth.repository.RefreshTokenRepository;
import com.tripbler.backend.common.exception.BusinessException;
import com.tripbler.backend.common.exception.ErrorCode;
import com.tripbler.backend.user.entity.SocialAccount;
import com.tripbler.backend.user.entity.SocialProvider;
import com.tripbler.backend.user.entity.User;
import com.tripbler.backend.user.repository.SocialAccountRepository;
import com.tripbler.backend.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AccountDeletionServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private SocialAccountRepository socialAccountRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private PasswordResetVerificationRepository
        passwordResetVerificationRepository;

    @Mock
    private FindIdVerificationRepository findIdVerificationRepository;

    @InjectMocks
    private AccountDeletionService accountDeletionService;

    @Test
    @DisplayName("계정 탈퇴 시 사용자와 관련된 모든 인증 정보를 삭제한다")
    void deleteAccountDeletesAllRelatedData() {

        Long userId = 1L;
        String loginId = "testuser01";
        String googleEmail = "testuser@gmail.com";

        User user = mock(User.class);
        SocialAccount googleAccount =
            mock(SocialAccount.class);

        when(
            userRepository.findById(userId)
        ).thenReturn(
            Optional.of(user)
        );

        when(
            user.getLoginId()
        ).thenReturn(
            loginId
        );

        when(
            socialAccountRepository.findAllByUserId(userId)
        ).thenReturn(
            List.of(googleAccount)
        );

        when(
            googleAccount.getProvider()
        ).thenReturn(
            SocialProvider.GOOGLE
        );

        when(
            googleAccount.getProviderEmail()
        ).thenReturn(
            googleEmail
        );

        accountDeletionService.deleteAccount(
            userId
        );

        verify(
            findIdVerificationRepository
        ).deleteAllByEmailIgnoreCase(
            googleEmail
        );

        verify(
            passwordResetTokenRepository
        ).deleteAllByUserId(
            userId
        );

        verify(
            passwordResetVerificationRepository
        ).deleteAllByLoginIdIgnoreCase(
            loginId
        );

        verify(
            refreshTokenRepository
        ).deleteByUser(
            user
        );

        verify(
            socialAccountRepository
        ).deleteAllByUserId(
            userId
        );

        verify(
            userRepository
        ).flush();

        verify(
            userRepository
        ).delete(
            user
        );
    }

    @Test
    @DisplayName("연동된 소셜 계정이 없어도 계정 탈퇴를 정상 처리한다")
    void deleteAccountWithoutSocialAccount() {

        Long userId = 1L;
        String loginId = "testuser01";

        User user = mock(User.class);

        when(
            userRepository.findById(userId)
        ).thenReturn(
            Optional.of(user)
        );

        when(
            user.getLoginId()
        ).thenReturn(
            loginId
        );

        when(
            socialAccountRepository.findAllByUserId(userId)
        ).thenReturn(
            List.of()
        );

        accountDeletionService.deleteAccount(
            userId
        );

        verify(
            passwordResetTokenRepository
        ).deleteAllByUserId(
            userId
        );

        verify(
            passwordResetVerificationRepository
        ).deleteAllByLoginIdIgnoreCase(
            loginId
        );

        verify(
            refreshTokenRepository
        ).deleteByUser(
            user
        );

        verify(
            socialAccountRepository
        ).deleteAllByUserId(
            userId
        );

        verify(
            userRepository
        ).flush();

        verify(
            userRepository
        ).delete(
            user
        );
    }

    @Test
    @DisplayName("존재하지 않는 사용자의 계정 탈퇴 요청은 USER_NOT_FOUND를 발생시킨다")
    void deleteAccountFailsWhenUserDoesNotExist() {

        Long userId = 999L;

        when(
            userRepository.findById(userId)
        ).thenReturn(
            Optional.empty()
        );

        BusinessException exception =
            assertThrows(
                BusinessException.class,
                () ->
                    accountDeletionService.deleteAccount(
                        userId
                    )
            );

        assertEquals(
            ErrorCode.USER_NOT_FOUND,
            exception.getErrorCode()
        );

        verify(
            userRepository
        ).findById(
            userId
        );

        verifyNoInteractions(
            refreshTokenRepository,
            socialAccountRepository,
            passwordResetTokenRepository,
            passwordResetVerificationRepository,
            findIdVerificationRepository
        );
    }
}