package com.tripbler.backend.auth.repository;

import com.tripbler.backend.auth.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PasswordResetTokenRepository
    extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(
        String tokenHash
    );

    void deleteAllByUserId(
        Long userId
    );
}