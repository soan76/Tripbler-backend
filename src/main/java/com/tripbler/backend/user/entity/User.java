package com.tripbler.backend.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 로그인에 사용하는 아이디
    @Column(nullable = false, unique = true)
    private String loginId;

    // 앱 내부에 표시할 닉네임
    @Column(length = 20, unique = true)
    private String nickname;

    // 프로필 이미지가 저장된 위치를 식별하는 저장소 키
    @Column(length = 500)
    private String profileImageKey;
    
    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    protected User() {
    }

    public User(
        String loginId,
        String nickname,
        String password
    ) {
        this.loginId = loginId;
        this.nickname = nickname;
        this.password = password;
        this.role = UserRole.USER;
    }

    public Long getId() {
        return id;
    }

    public String getLoginId() {
        return loginId;
    }

    public String getNickname() {
        return nickname;
    }

    public String getProfileImageKey() {
        return profileImageKey;
    }

    public String getPassword() {
        return password;
    }

    public UserRole getRole() {
        return role;
    }

    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    public void changeProfileImageKey(
        String profileImageKey
    ) {
        this.profileImageKey = profileImageKey;
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }
}