package com.tripbler.backend.auth.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(
    name = "password_reset_tokens",
    indexes = {
        @Index(
            name = "idx_password_reset_token_hash",
            columnList = "token_hash"
        ),
        @Index(
            name = "idx_password_reset_token_user_id",
            columnList = "user_id"
        )
    }
)
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 비밀번호를 변경할 Tripbler 사용자 ID
    @Column(
        name = "user_id",
        nullable = false
    )
    private Long userId;

    // resetToken 원문이 아닌 SHA-256 해시값
    @Column(
        name = "token_hash",
        nullable = false,
        unique = true,
        length = 64
    )
    private String tokenHash;

    @Column(
        name = "expires_at",
        nullable = false
    )
    private Instant expiresAt;

    @Column(
        name = "used",
        nullable = false
    )
    private boolean used = false;

    protected PasswordResetToken() {
    }

    public PasswordResetToken(
        Long userId,
        String tokenHash,
        Instant expiresAt
    ) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isUsed() {
        return used;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public void markUsed() {
        used = true;
    }
}