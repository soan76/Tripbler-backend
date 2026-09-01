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