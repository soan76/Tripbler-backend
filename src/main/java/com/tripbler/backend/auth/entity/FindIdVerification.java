package com.tripbler.backend.auth.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(
    name = "find_id_verifications",
    indexes = {
        @Index(
            name = "idx_find_id_verification_email",
            columnList = "email"
        )
    }
)
public class FindIdVerification {

    // 같은 인증코드로 허용하는 오답 횟수. 이 횟수에 도달하면 코드를 폐기한다.
    public static final int MAX_FAILED_ATTEMPTS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 아이디 찾기에 사용한 연동 이메일
    @Column(
        name = "email",
        nullable = false,
        length = 320
    )
    private String email;

    // 인증코드 원문이 아닌 해시값을 저장한다.
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

    protected FindIdVerification() {
    }

    public FindIdVerification(
        String email,
        String codeHash,
        Instant expiresAt
    ) {
        this.email = email;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
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