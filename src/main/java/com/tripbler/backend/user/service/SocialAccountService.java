package com.tripbler.backend.user.service;

import com.tripbler.backend.common.exception.BusinessException;
import com.tripbler.backend.common.exception.ErrorCode;
import com.tripbler.backend.user.entity.SocialAccount;
import com.tripbler.backend.user.entity.SocialProvider;
import com.tripbler.backend.user.entity.User;
import com.tripbler.backend.user.exception.SocialAccountAlreadyLinkedException;
import com.tripbler.backend.user.exception.SocialAccountUsedByAnotherUserException;
import com.tripbler.backend.user.repository.SocialAccountRepository;
import com.tripbler.backend.user.repository.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SocialAccountService {

    private final SocialAccountRepository socialAccountRepository;
    private final UserRepository userRepository;

    public SocialAccountService(
        SocialAccountRepository socialAccountRepository,
        UserRepository userRepository
    ) {
        this.socialAccountRepository = socialAccountRepository;
        this.userRepository = userRepository;
    }

    // 플랫폼 인증이 완료된 계정을 현재 Tripbler 사용자에게 연동한다.
    public SocialAccount linkSocialAccount(
        Long userId,
        SocialProvider provider,
        String providerUserId
    ) {
        User user = userRepository.findById(userId)
            .orElseThrow(
                () -> new BusinessException(ErrorCode.USER_NOT_FOUND)
            );

        if (socialAccountRepository.existsByUserIdAndProvider(
            userId,
            provider
        )) {
            throw new SocialAccountAlreadyLinkedException();
        }

        if (socialAccountRepository.existsByProviderAndProviderUserId(
            provider,
            providerUserId
        )) {
            throw new SocialAccountUsedByAnotherUserException();
        }

        SocialAccount socialAccount = new SocialAccount(
            user,
            provider,
            providerUserId
        );

        return socialAccountRepository.save(socialAccount);
    }
}