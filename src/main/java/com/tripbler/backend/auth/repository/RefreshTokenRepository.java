package com.tripbler.backend.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tripbler.backend.auth.entity.RefreshToken;
import com.tripbler.backend.user.entity.User;

public interface RefreshTokenRepository
    extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(
        String token
    );

    Optional<RefreshToken> findByUser(
        User user
    );

    void deleteByUser(
        User user
    );
}