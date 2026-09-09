package com.tripbler.backend.user.service;

import com.tripbler.backend.user.dto.UserResponse;
import com.tripbler.backend.user.entity.User;
import com.tripbler.backend.user.exception.InvalidProfileImageException;
import com.tripbler.backend.user.exception.UserNotFoundException;
import com.tripbler.backend.user.repository.UserRepository;
import com.tripbler.backend.user.storage.ProfileImageStorage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileImageServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProfileImageStorage profileImageStorage;

    private ProfileImageService profileImageService;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager
            .initSynchronization();

        profileImageService =
            new ProfileImageService(
                new UserFinder(userRepository),
                profileImageStorage
            );
    }

    @AfterEach
    void tearDown() {
        if (
            TransactionSynchronizationManager
                .isSynchronizationActive()
        ) {
            TransactionSynchronizationManager
                .clearSynchronization();
        }
    }

    @Test
    void updateProfileImageSucceedsWithValidJpeg() {

        // given
        Long userId = 1L;

        User user = new User(
            "testuser01",
            "여행자",
            "encodedPassword"
        );

        MockMultipartFile file =
            new MockMultipartFile(
                "file",
                "profile.jpg",
                "image/jpeg",
                "image-data".getBytes()
            );

        String imageKey =
            "profiles/1/new-image.jpg";

        String imageUrl =
            "http://localhost:8080/uploads/"
                + imageKey;

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));

        when(
            profileImageStorage.save(
                userId,
                file
            )
        ).thenReturn(imageKey);

        when(
            profileImageStorage.resolveUrl(
                imageKey
            )
        ).thenReturn(imageUrl);

        // when
        UserResponse response =
            profileImageService.updateProfileImage(
                userId,
                file
            );

        // then
        assertThat(user.getProfileImageKey())
            .isEqualTo(imageKey);

        assertThat(response.profileImageUrl())
            .isEqualTo(imageUrl);

        verify(profileImageStorage)
            .save(userId, file);

        verify(
            profileImageStorage,
            never()
        ).delete(any());

        commitTransaction();

        verify(
            profileImageStorage,
            never()
        ).delete(any());
    }

    @Test
    void updateProfileImageDeletesPreviousImageAfterCommit() {

        // given
        Long userId = 1L;

        User user = new User(
            "testuser01",
            "여행자",
            "encodedPassword"
        );

        String previousImageKey =
            "profiles/1/old-image.jpg";

        String newImageKey =
            "profiles/1/new-image.jpg";

        user.changeProfileImageKey(
            previousImageKey
        );

        MockMultipartFile file =
            new MockMultipartFile(
                "file",
                "profile.jpg",
                "image/jpeg",
                "image-data".getBytes()
            );

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));

        when(
            profileImageStorage.save(
                userId,
                file
            )
        ).thenReturn(newImageKey);

        when(
            profileImageStorage.resolveUrl(
                newImageKey
            )
        ).thenReturn(
            "http://localhost:8080/uploads/"
                + newImageKey
        );

        // when
        profileImageService.updateProfileImage(
            userId,
            file
        );

        // then
        assertThat(user.getProfileImageKey())
            .isEqualTo(newImageKey);

        verify(profileImageStorage)
            .save(userId, file);

        // 아직 DB 커밋 전이므로 기존 파일을 삭제하면 안됨.
        verify(
            profileImageStorage,
            never()
        ).delete(previousImageKey);

        // commit
        commitTransaction();

        // DB 커밋이 완료된 뒤 기존 파일을 삭제한다.
        verify(profileImageStorage)
            .delete(previousImageKey);
    }

    @Test
    void updateProfileImageDeletesNewImageWhenTransactionRollsBack() {

        // given
        Long userId = 1L;

        User user = new User(
            "testuser01",
            "여행자",
            "encodedPassword"
        );

        String previousImageKey =
            "profiles/1/old-image.jpg";

        String newImageKey =
            "profiles/1/new-image.jpg";

        user.changeProfileImageKey(
            previousImageKey
        );

        MockMultipartFile file =
            new MockMultipartFile(
                "file",
                "profile.jpg",
                "image/jpeg",
                "image-data".getBytes()
            );

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));

        when(
            profileImageStorage.save(
                userId,
                file
            )
        ).thenReturn(newImageKey);

        when(
            profileImageStorage.resolveUrl(
                newImageKey
            )
        ).thenReturn(
            "http://localhost:8080/uploads/"
                + newImageKey
        );

        // when
        profileImageService.updateProfileImage(
            userId,
            file
        );

        verify(
            profileImageStorage,
            never()
        ).delete(previousImageKey);

        verify(
            profileImageStorage,
            never()
        ).delete(newImageKey);

        rollbackTransaction();

        // then
        verify(profileImageStorage)
            .delete(newImageKey);

        verify(
            profileImageStorage,
            never()
        ).delete(previousImageKey);
    }

    @Test
    void updateProfileImageRejectsEmptyFile() {

        // given
        MockMultipartFile file =
            new MockMultipartFile(
                "file",
                "profile.jpg",
                "image/jpeg",
                new byte[0]
            );

        // when & then
        assertThatThrownBy(
            () ->
                profileImageService
                    .updateProfileImage(
                        1L,
                        file
                    )
        )
            .isInstanceOf(
                InvalidProfileImageException.class
            );

        verify(
            profileImageStorage,
            never()
        ).save(anyLong(), any());
    }

    @Test
    void updateProfileImageRejectsFileLargerThanFiveMb() {

        // given
        byte[] oversizedContent =
            new byte[
                (5 * 1024 * 1024) + 1
            ];

        MockMultipartFile file =
            new MockMultipartFile(
                "file",
                "profile.jpg",
                "image/jpeg",
                oversizedContent
            );

        // when & then
        assertThatThrownBy(
            () ->
                profileImageService
                    .updateProfileImage(
                        1L,
                        file
                    )
        )
            .isInstanceOf(
                InvalidProfileImageException.class
            );

        verify(
            profileImageStorage,
            never()
        ).save(anyLong(), any());
    }

    @Test
    void updateProfileImageRejectsUnsupportedContentType() {

        // given
        MockMultipartFile file =
            new MockMultipartFile(
                "file",
                "profile.jpg",
                "text/plain",
                "not-image".getBytes()
            );

        // when & then
        assertThatThrownBy(
            () ->
                profileImageService
                    .updateProfileImage(
                        1L,
                        file
                    )
        )
            .isInstanceOf(
                InvalidProfileImageException.class
            );

        verify(
            profileImageStorage,
            never()
        ).save(anyLong(), any());
    }

    @Test
    void updateProfileImageRejectsUnsupportedExtension() {

        // given
        MockMultipartFile file =
            new MockMultipartFile(
                "file",
                "profile.bmp",
                "image/jpeg",
                "image-data".getBytes()
            );

        // when & then
        assertThatThrownBy(
            () ->
                profileImageService
                    .updateProfileImage(
                        1L,
                        file
                    )
        )
            .isInstanceOf(
                InvalidProfileImageException.class
            );

        verify(
            profileImageStorage,
            never()
        ).save(anyLong(), any());
    }

    @Test
    void updateProfileImageSucceedsWithValidGif() {

        Long userId = 1L;

        User user = new User(
            "testuser01",
            "여행자",
            "encodedPassword"
        );

        MockMultipartFile file =
            new MockMultipartFile(
                "file",
                "profile.gif",
                "image/gif",
                "gif-data".getBytes()
            );

        String imageKey =
            "profiles/1/profile.gif";

        String imageUrl =
            "http://localhost:8080/uploads/"
                + imageKey;

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));

        when(
            profileImageStorage.save(
                userId,
                file
            )
        ).thenReturn(imageKey);

        when(
            profileImageStorage.resolveUrl(
                imageKey
            )
        ).thenReturn(imageUrl);

        UserResponse response =
            profileImageService.updateProfileImage(
                userId,
                file
            );

        assertThat(user.getProfileImageKey())
            .isEqualTo(imageKey);

        assertThat(response.profileImageUrl())
            .isEqualTo(imageUrl);

        verify(profileImageStorage)
            .save(userId, file);

        commitTransaction();
    }

    @Test
    void deleteProfileImageDeletesStoredImageAndClearsKey() {

        // given
        Long userId = 1L;

        User user = new User(
            "testuser01",
            "여행자",
            "encodedPassword"
        );

        String imageKey =
            "profiles/1/profile.jpg";

        user.changeProfileImageKey(
            imageKey
        );

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));

        // when
        profileImageService.deleteProfileImage(
            userId
        );

        // DB 객체에서는 이미지 키가 먼저 제거된다.
        assertThat(user.getProfileImageKey())
            .isNull();

        // 아직 커밋 전이므로 실제 파일은 삭제하지 않는다.
        verify(
            profileImageStorage,
            never()
        ).delete(imageKey);

        // commit
        commitTransaction();

        // 커밋 후 실제 파일을 삭제한다.
        verify(profileImageStorage)
            .delete(imageKey);
    }

    @Test
    void deleteProfileImageDoesNotDeleteStoredFileWhenTransactionRollsBack() {

        // given
        Long userId = 1L;

        User user = new User(
            "testuser01",
            "여행자",
            "encodedPassword"
        );

        String imageKey =
            "profiles/1/profile.jpg";

        user.changeProfileImageKey(
            imageKey
        );

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));

        // when
        profileImageService.deleteProfileImage(
            userId
        );

        // 아직 롤백 전이므로 실제 파일은 삭제하지 않는다.
        verify(
            profileImageStorage,
            never()
        ).delete(imageKey);

        rollbackTransaction();

        // then
        // 롤백되면 실제 파일은 그대로 유지되어야 한다.
        verify(
            profileImageStorage,
            never()
        ).delete(imageKey);
    }

    @Test
    void deleteProfileImageDoesNothingWhenUserHasNoImage() {

        // given
        Long userId = 1L;

        User user = new User(
            "testuser01",
            "여행자",
            "encodedPassword"
        );

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));

        // when
        profileImageService.deleteProfileImage(
            userId
        );

        // then
        verify(
            profileImageStorage,
            never()
        ).delete(any());

        assertThat(user.getProfileImageKey())
            .isNull();
    }

    @Test
    void updateProfileImageThrowsWhenUserDoesNotExist() {

        // given
        Long userId = 999L;

        MockMultipartFile file =
            new MockMultipartFile(
                "file",
                "profile.jpg",
                "image/jpeg",
                "image-data".getBytes()
            );

        when(userRepository.findById(userId))
            .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(
            () ->
                profileImageService
                    .updateProfileImage(
                        userId,
                        file
                    )
        )
            .isInstanceOf(
                UserNotFoundException.class
            );

        verify(
            profileImageStorage,
            never()
        ).save(anyLong(), any());
    }

    private void commitTransaction() {

        var synchronizations =
            TransactionSynchronizationManager
                .getSynchronizations();

        synchronizations.forEach(
            TransactionSynchronization::afterCommit
        );

        synchronizations.forEach(
            synchronization ->
                synchronization.afterCompletion(
                    TransactionSynchronization
                        .STATUS_COMMITTED
                )
        );

        TransactionSynchronizationManager
            .clearSynchronization();
    }

    private void rollbackTransaction() {

        var synchronizations =
            TransactionSynchronizationManager
                .getSynchronizations();

        synchronizations.forEach(
            synchronization ->
                synchronization.afterCompletion(
                    TransactionSynchronization
                        .STATUS_ROLLED_BACK
                )
        );

        TransactionSynchronizationManager
            .clearSynchronization();
    }
}