package com.tripbler.backend.auth.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

@Service
public class AccountDeletionService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordResetVerificationRepository
        passwordResetVerificationRepository;
    private final FindIdVerificationRepository findIdVerificationRepository;

    // 생성자 주입을 통해 필요한 리포지토리들을 초기화한다.
    public AccountDeletionService(
        UserRepository userRepository,
        RefreshTokenRepository refreshTokenRepository,
        SocialAccountRepository socialAccountRepository,
        PasswordResetTokenRepository passwordResetTokenRepository,
        PasswordResetVerificationRepository
            passwordResetVerificationRepository,
        FindIdVerificationRepository findIdVerificationRepository
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.socialAccountRepository = socialAccountRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordResetVerificationRepository =
            passwordResetVerificationRepository;
        this.findIdVerificationRepository = findIdVerificationRepository;
    }

    // 사용자 관련 인증 및 연동 데이터를 모두 정리한 뒤 계정을 삭제한다.
    @Transactional
    public void deleteAccount(Long userId) {
        User user = userRepository
            .findById(userId)
            .orElseThrow(
                () -> new BusinessException(
                    ErrorCode.USER_NOT_FOUND
                )
            );

        String loginId = user.getLoginId();

        List<SocialAccount> socialAccounts =
            socialAccountRepository.findAllByUserId(userId);

        deleteFindIdVerifications(socialAccounts);

        passwordResetTokenRepository.deleteAllByUserId(
            userId
        );

        passwordResetVerificationRepository
            .deleteAllByLoginIdIgnoreCase(
                loginId
            );

        refreshTokenRepository.deleteByUser(
            user
        );

        socialAccountRepository.deleteAllByUserId(
            userId
        );

        // FK 자식 데이터 삭제를 DB에 먼저 반영한 뒤 User를 삭제한다.
        userRepository.flush();

        userRepository.delete(
            user
        );
    }

    // 아이디 찾기에 사용된 Google 연동 이메일의 인증 정보를 삭제한다.
    private void deleteFindIdVerifications(
        List<SocialAccount> socialAccounts
    ) {
        for (SocialAccount socialAccount : socialAccounts) {
            if (socialAccount.getProvider() != SocialProvider.GOOGLE) {
                continue;
            }

            String email = socialAccount.getProviderEmail();

            if (email == null || email.isBlank()) {
                continue;
            }

            findIdVerificationRepository
                .deleteAllByEmailIgnoreCase(
                    email
                );
        }
    }
}