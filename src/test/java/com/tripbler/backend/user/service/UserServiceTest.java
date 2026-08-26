package com.tripbler.backend.user.service;

import com.tripbler.backend.user.dto.LoginIdAvailabilityResponse;
import com.tripbler.backend.user.entity.User;
import com.tripbler.backend.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(
            userRepository,
            passwordEncoder
        );
    }

    @Test
    void checkLoginIdAvailabilityReturnsTrueWhenLoginIdDoesNotExist() {

        // given
        String loginId = "newuser123";

        when(userRepository.findByLoginId(loginId))
            .thenReturn(Optional.empty());

        // when
        LoginIdAvailabilityResponse response =
            userService.checkLoginIdAvailability(loginId);

        // then
        assertThat(response.loginId())
            .isEqualTo(loginId);

        assertThat(response.available())
            .isTrue();
    }

    @Test
    void checkLoginIdAvailabilityReturnsFalseWhenLoginIdExists() {

        // given
        String loginId = "test0908";

        User existingUser = new User(
            loginId,
            "여행자",
            "test0908@tripbler.com",
            "encodedPassword"
        );

        when(userRepository.findByLoginId(loginId))
            .thenReturn(Optional.of(existingUser));

        // when
        LoginIdAvailabilityResponse response =
            userService.checkLoginIdAvailability(loginId);

        // then
        assertThat(response.loginId())
            .isEqualTo(loginId);

        assertThat(response.available())
            .isFalse();
    }
}