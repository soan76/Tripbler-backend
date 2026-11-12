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

    // 같은 인증코드로 허용하는 오답 횟수. 이 횟수에 도달하면 코드를 폐기한다.
    public static final int MAX_FAILED_ATTEMPTS = 5;

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

    // 오답을 한 번 기록한다. 폐기 여부는 isAttemptsExhausted()로 판단한다.
    public void recordFailedAttempt() {
        attemptCount++;
    }

    // 오답이 상한에 도달해 더 이상 쓸 수 없는 코드인지 확인한다.
    public boolean isAttemptsExhausted() {
        return attemptCount >= MAX_FAILED_ATTEMPTS;
    }

    public void markVerified() {
        verified = true;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}