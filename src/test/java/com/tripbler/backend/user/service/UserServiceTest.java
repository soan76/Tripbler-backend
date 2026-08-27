package com.tripbler.backend.user.service;

import com.tripbler.backend.user.dto.LoginIdAvailabilityResponse;
import com.tripbler.backend.user.dto.UserCreateRequest;
import com.tripbler.backend.user.dto.UserResponse;
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
import static org.mockito.ArgumentMatchers.any;
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

    @Test
    void createUserSucceedsWithoutNickname() {

        // given
        UserCreateRequest request = new UserCreateRequest(
            "nonicktest01",
            null,
            "password123"
        );

        when(userRepository.findByLoginId(request.loginId()))
            .thenReturn(Optional.empty());

        when(passwordEncoder.encode(request.password()))
            .thenReturn("encodedPassword");

        when(userRepository.save(any(User.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        UserResponse response =
            userService.createUser(request);

        // then
        assertThat(response.loginId())
            .isEqualTo("nonicktest01");

        assertThat(response.nickname())
            .isNull();
    }

    @Test
    void createUserSucceedsWithOneCharacterNickname() {

        // given
        UserCreateRequest request = new UserCreateRequest(
            "nicktest01",
            "가",
            "password123"
        );

        when(userRepository.findByLoginId(request.loginId()))
            .thenReturn(Optional.empty());

        when(passwordEncoder.encode(request.password()))
            .thenReturn("encodedPassword");

        when(userRepository.save(any(User.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        UserResponse response =
            userService.createUser(request);

        // then
        assertThat(response.loginId())
            .isEqualTo("nicktest01");

        assertThat(response.nickname())
            .isEqualTo("가");
    }
}