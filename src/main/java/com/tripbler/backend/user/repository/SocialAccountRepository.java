package com.tripbler.backend.user.repository;

import com.tripbler.backend.user.entity.SocialAccount;
import com.tripbler.backend.user.entity.SocialProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SocialAccountRepository
    extends JpaRepository<SocialAccount, Long> {

    // 플랫폼 고유 ID로 연동된 계정을 조회한다.
    Optional<SocialAccount> findByProviderAndProviderUserId(
        SocialProvider provider,
        String providerUserId
    );

    // 사용자가 해당 플랫폼 계정을 이미 연동했는지 확인한다.
    boolean existsByUserIdAndProvider(
        Long userId,
        SocialProvider provider
    );

    boolean existsByProviderAndProviderUserId(
        SocialProvider provider,
        String providerUserId
    );
}