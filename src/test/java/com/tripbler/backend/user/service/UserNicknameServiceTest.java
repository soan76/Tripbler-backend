package com.tripbler.backend.user.service;

import com.tripbler.backend.user.dto.UserNicknameChangeRequest;
import com.tripbler.backend.user.dto.UserResponse;
import com.tripbler.backend.user.entity.User;
import com.tripbler.backend.user.exception.UserNotFoundException;
import com.tripbler.backend.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserNicknameServiceTest {

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
    @DisplayName("닉네임 변경 시 사용자와 응답의 닉네임이 변경된다")
    void changeNicknameUpdatesUserAndReturnsResponse() {

        // given
        Long userId = 1L;

        User user = new User(
            "testuser01",
            "기존닉네임",
            "encodedPassword"
        );

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));

        UserNicknameChangeRequest request =
            new UserNicknameChangeRequest(
                "  새닉네임  "
            );

        // DTO 생성 시 공통 정규화가 적용되는지 함께 확인한다.
        assertThat(request.nickname())
            .isEqualTo("새닉네임");

        // when
        UserResponse response =
            userService.changeNickname(
                userId,
                request
            );

        // then
        assertThat(user.getNickname())
            .isEqualTo("새닉네임");

        assertThat(response.loginId())
            .isEqualTo("testuser01");

        assertThat(response.nickname())
            .isEqualTo("새닉네임");

        verify(userRepository)
            .findById(userId);
    }

    @Test
    @DisplayName("닉네임 변경 대상 사용자가 없으면 UserNotFoundException을 발생시킨다")
    void changeNicknameThrowsWhenUserDoesNotExist() {

        // given
        Long userId = 999L;

        when(userRepository.findById(userId))
            .thenReturn(Optional.empty());

        UserNicknameChangeRequest request =
            new UserNicknameChangeRequest(
                "새닉네임"
            );

        // when / then
        assertThatThrownBy(
            () -> userService.changeNickname(
                userId,
                request
            )
        )
        .isInstanceOf(
            UserNotFoundException.class
        );

        verify(userRepository)
            .findById(userId);
    }
}