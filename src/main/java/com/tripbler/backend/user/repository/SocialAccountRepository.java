package com.tripbler.backend.user.repository;

import com.tripbler.backend.user.entity.SocialAccount;
import com.tripbler.backend.user.entity.SocialProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SocialAccountRepository
    extends JpaRepository<SocialAccount, Long> {

    // 플랫폼 고유 ID로 연동된 계정을 조회한다.
    Optional<SocialAccount> findByProviderAndProviderUserId(
        SocialProvider provider,
        String providerUserId
    );

    // 플랫폼과 인증된 이메일로 연동 계정을 조회한다.
    Optional<SocialAccount> findByProviderAndProviderEmailIgnoreCase(
        SocialProvider provider,
        String providerEmail
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

    // 현재 사용자에게 연동된 소셜 계정을 조회한다.
    List<SocialAccount> findAllByUserId(Long userId);

    // 사용자의 특정 소셜 플랫폼 연동 정보를 삭제한다.
    void deleteByUserIdAndProvider(
        Long userId,
        SocialProvider provider
    );
}