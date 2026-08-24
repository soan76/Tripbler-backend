package com.tripbler.backend.auth.entity;

import java.time.LocalDateTime;

import com.tripbler.backend.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "user_id",
        nullable = false,
        unique = true
    )
    private User user;

    @Column(
        nullable = false,
        unique = true,
        length = 512
    )
    private String token;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    protected RefreshToken() {
    }

    public RefreshToken(
        User user,
        String token,
        LocalDateTime expiresAt
    ) {
        this.user = user;
        this.token = token;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getToken() {
        return token;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpired() {
        return LocalDateTime.now()
            .isAfter(expiresAt);
    }

    public void update(
        String token,
        LocalDateTime expiresAt
    ) {
        this.token = token;
        this.expiresAt = expiresAt;
    }
}