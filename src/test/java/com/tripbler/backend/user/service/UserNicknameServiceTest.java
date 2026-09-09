package com.tripbler.backend.user.service;

import com.tripbler.backend.user.dto.UserNicknameChangeRequest;
import com.tripbler.backend.user.dto.UserResponse;
import com.tripbler.backend.user.entity.User;
import com.tripbler.backend.user.exception.UserNotFoundException;
import com.tripbler.backend.user.exception.DuplicateNicknameException;
import com.tripbler.backend.user.repository.UserRepository;
import com.tripbler.backend.user.storage.ProfileImageStorage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;
import static org.mockito.Mockito.doThrow;

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

    @Mock
    private ProfileImageStorage profileImageStorage;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(
            userRepository,
            passwordEncoder,
            new UserFinder(userRepository),
            profileImageStorage
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
    @DisplayName("닉네임 변경 후에도 기존 프로필 이미지 URL이 유지된다")
    void changeNicknameKeepsProfileImageUrl() {

        // given
        Long userId = 1L;

        User user = new User(
            "testuser01",
            "기존닉네임",
            "encodedPassword"
        );

        String imageKey =
            "profiles/1/profile.jpg";

        String imageUrl =
            "http://localhost:8080/uploads/"
                + imageKey;

        user.changeProfileImageKey(
            imageKey
        );

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));

        when(
            profileImageStorage.resolveUrl(
                imageKey
            )
        ).thenReturn(imageUrl);

        UserNicknameChangeRequest request =
            new UserNicknameChangeRequest(
                "새닉네임"
            );

        // when
        UserResponse response =
            userService.changeNickname(
                userId,
                request
            );

        // then
        assertThat(response.nickname())
            .isEqualTo("새닉네임");

        assertThat(response.profileImageUrl())
            .isEqualTo(imageUrl);

        verify(profileImageStorage)
            .resolveUrl(imageKey);
    }

    @Test
    @DisplayName(
        "닉네임 변경 시 DB UNIQUE 충돌이 발생하면 DuplicateNicknameException을 발생시킨다"
    )
    void changeNicknameConvertsDatabaseUniqueViolationToDuplicateNicknameException() {

        // given
        Long userId = 1L;

        User user = new User(
            "testuser01",
            "기존닉네임",
            "encodedPassword"
        );

        UserNicknameChangeRequest request =
            new UserNicknameChangeRequest(
                "여행러버"
            );

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));

        when(
            userRepository.existsByNicknameAndIdNot(
                request.nickname(),
                userId
            )
        ).thenReturn(false);

        doThrow(
            new DataIntegrityViolationException(
                "unique constraint violation"
            )
        )
            .when(userRepository)
            .flush();

        // when & then
        assertThatThrownBy(
            () -> userService.changeNickname(
                userId,
                request
            )
        )
            .isInstanceOf(
                DuplicateNicknameException.class
            );

        verify(userRepository)
            .flush();
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