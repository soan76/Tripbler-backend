package com.tripbler.backend.auth.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(
    name = "password_reset_verifications",
    indexes = {
        @Index(
            name = "idx_password_reset_verification_email",
            columnList = "email"
        ),
        @Index(
            name = "idx_password_reset_verification_login_id",
            columnList = "login_id"
        )
    }
)
public class PasswordResetVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 비밀번호를 재설정할 Tripbler 아이디
    @Column(
        name = "login_id",
        nullable = false,
        length = 30
    )
    private String loginId;

    // 해당 Tripbler 계정에 연동된 Google 이메일
    @Column(
        name = "email",
        nullable = false,
        length = 320
    )
    private String email;

    // 인증코드 원문이 아닌 해시값 저장
    @Column(
        name = "code_hash",
        nullable = false,
        length = 255
    )
    private String codeHash;

    @Column(
        name = "expires_at",
        nullable = false
    )
    private Instant expiresAt;

    @Column(
        name = "attempt_count",
        nullable = false
    )
    private int attemptCount = 0;

    @Column(
        name = "verified",
        nullable = false
    )
    private boolean verified = false;

    protected PasswordResetVerification() {
    }

    public PasswordResetVerification(
        String loginId,
        String email,
        String codeHash,
        Instant expiresAt
    ) {
        this.loginId = loginId;
        this.email = email;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public String getLoginId() {
        return loginId;
    }

    public String getEmail() {
        return email;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public boolean isVerified() {
        return verified;
    }

    public void increaseAttemptCount() {
        attemptCount++;
    }

    public void markVerified() {
        verified = true;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}