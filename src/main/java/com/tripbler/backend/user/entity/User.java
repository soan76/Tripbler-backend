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

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    protected User() {
    }

    public User(
        String email,
        String password
    ) {
        this.email = email;
        this.password = password;
        this.role = UserRole.USER;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public void changeEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public UserRole getRole() {
        return role;
    }
}