package com.tripbler.backend.user.entity;

import jakarta.persistence.*;

@Entity
@Table(
    name = "social_accounts",
    uniqueConstraints = {
        // 하나의 플랫폼 계정이 여러 Tripbler 계정에 연결되는 것을 방지한다.
        @UniqueConstraint(
            name = "uk_social_provider_user",
            columnNames = {"provider", "provider_user_id"}
        ),

        // 한 사용자가 같은 플랫폼 계정을 여러 개 연동하는 것을 방지한다.
        @UniqueConstraint(
            name = "uk_user_provider",
            columnNames = {"user_id", "provider"}
        )
    }
)
public class SocialAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 외부 플랫폼 계정과 연결된 Tripbler 사용자를 참조한다.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "user_id",
        nullable = false
    )
    private User user;

    // GOOGLE, NAVER, KAKAO 등의 플랫폼 종류를 저장한다.
    @Enumerated(EnumType.STRING)
    @Column(
        name = "provider",
        nullable = false,
        length = 20
    )
    private SocialProvider provider;

    // 각 플랫폼에서 발급하는 사용자의 고유 식별자를 저장한다.
    @Column(
        name = "provider_user_id",
        nullable = false,
        length = 255
    )
    private String providerUserId;

    // 플랫폼에서 인증된 이메일을 저장한다.
    @Column(
        name = "provider_email",
        length = 320
    )
    private String providerEmail;

    protected SocialAccount() {
    }

    public SocialAccount(
        User user,
        SocialProvider provider,
        String providerUserId,
        String providerEmail
    ) {
        this.user = user;
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.providerEmail = providerEmail;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public SocialProvider getProvider() {
        return provider;
    }

    public String getProviderUserId() {
        return providerUserId;
    }

    public String getProviderEmail() {
        return providerEmail;
    }
}