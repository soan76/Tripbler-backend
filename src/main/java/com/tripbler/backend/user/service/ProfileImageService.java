package com.tripbler.backend.user.service;

import com.tripbler.backend.user.dto.UserResponse;
import com.tripbler.backend.user.entity.User;
import com.tripbler.backend.user.exception.InvalidProfileImageException;
import com.tripbler.backend.user.storage.ProfileImageStorage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Set;

@Service
public class ProfileImageService {

    private static final long MAX_FILE_SIZE =
        5L * 1024 * 1024;

    private static final Set<String>
        ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif"
        );

    private static final Set<String>
        ALLOWED_EXTENSIONS = Set.of(
            "jpg",
            "jpeg",
            "png",
            "webp",
            "gif"
        );

    private static final Logger log =
        LoggerFactory.getLogger(
            ProfileImageService.class
        );

    private final UserFinder userFinder;
    private final ProfileImageStorage profileImageStorage;

    public ProfileImageService(
        UserFinder userFinder,
        ProfileImageStorage profileImageStorage
    ) {
        this.userFinder = userFinder;
        this.profileImageStorage =
            profileImageStorage;
    }

    /**
     * 현재 사용자의 프로필 이미지를 저장하거나 교체한다.
     */
    @Transactional
    public UserResponse updateProfileImage(
        Long userId,
        MultipartFile file
    ) {
        validateProfileImage(file);

        User user =
            userFinder.getById(userId);

        ensureTransactionSynchronization();

        String previousImageKey =
            user.getProfileImageKey();

        String newImageKey =
            profileImageStorage.save(
                userId,
                file
            );

        try {
            registerUpdateSynchronization(
                previousImageKey,
                newImageKey
            );
        } catch (RuntimeException exception) {
            deleteQuietly(
                newImageKey,
                "트랜잭션 등록 실패 후 새 프로필 이미지 정리"
            );

            throw exception;
        }

        user.changeProfileImageKey(
            newImageKey
        );

        String profileImageUrl =
            profileImageStorage.resolveUrl(
                newImageKey
            );

        return UserResponse.from(
            user,
            profileImageUrl
        );
    }

    /**
     * 현재 사용자의 프로필 이미지를 삭제한다.
     */
    @Transactional
    public void deleteProfileImage(
        Long userId
    ) {
        User user =
            userFinder.getById(userId);

        String imageKey =
            user.getProfileImageKey();

        if (!StringUtils.hasText(imageKey)) {
            return;
        }

        ensureTransactionSynchronization();

        registerDeleteSynchronization(
            imageKey
        );

        user.changeProfileImageKey(
            null
        );
    }

    /**
     * 업로드된 프로필 이미지의 기본 유효성을 검증한다.
     */
    private void validateProfileImage(
        MultipartFile file
    ) {
        if (file == null ||
            file.isEmpty()) {
            throw new InvalidProfileImageException(
                "프로필 이미지 파일을 선택해 주세요."
            );
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new InvalidProfileImageException(
                "프로필 이미지 파일 크기는 5MB를 초과할 수 없습니다."
            );
        }

        String contentType =
            file.getContentType();

        if (!StringUtils.hasText(contentType) ||
            !ALLOWED_CONTENT_TYPES.contains(
                contentType.toLowerCase(
                    Locale.ROOT
                )
            )) {
            throw new InvalidProfileImageException(
                "허용되지 않는 프로필 이미지 파일 형식입니다."
            );
        }

        String extension =
            StringUtils.getFilenameExtension(
                file.getOriginalFilename()
            );

        if (!StringUtils.hasText(extension) ||
            !ALLOWED_EXTENSIONS.contains(
                extension.toLowerCase(
                    Locale.ROOT
                )
            )) {
            throw new InvalidProfileImageException(
                "허용되지 않는 프로필 이미지 파일 확장자입니다."
            );
        }
    }

    /**
     * 현재 프로필 이미지 작업이
     * 트랜잭션 안에서 실행 중인지 확인한다.
     */
    private void ensureTransactionSynchronization() {
        if (
            !TransactionSynchronizationManager
                .isSynchronizationActive()
        ) {
            throw new IllegalStateException(
                "프로필 이미지 처리를 위한 "
                    + "트랜잭션 동기화가 활성화되지 않았습니다."
            );
        }
    }

    /**
     * 이미지 교체 시 커밋 후 기존 이미지를 삭제하고,
     * 롤백 시 새로 저장한 이미지를 정리한다.
     */
    private void registerUpdateSynchronization(
        String previousImageKey,
        String newImageKey
    ) {
        TransactionSynchronizationManager
            .registerSynchronization(
                new TransactionSynchronization() {

                    @Override
                    public void afterCommit() {
                        if (
                            StringUtils.hasText(
                                previousImageKey
                            )
                        ) {
                            deleteQuietly(
                                previousImageKey,
                                "기존 프로필 이미지 정리"
                            );
                        }
                    }

                    @Override
                    public void afterCompletion(
                        int status
                    ) {
                        if (
                            status !=
                            TransactionSynchronization
                                .STATUS_COMMITTED
                        ) {
                            deleteQuietly(
                                newImageKey,
                                "롤백된 새 프로필 이미지 정리"
                            );
                        }
                    }
                }
            );
    }

    /**
     * 프로필 이미지 삭제 시
     * DB 커밋 이후 실제 파일을 삭제한다.
     */
    private void registerDeleteSynchronization(
        String imageKey
    ) {
        TransactionSynchronizationManager
            .registerSynchronization(
                new TransactionSynchronization() {

                    @Override
                    public void afterCommit() {
                        deleteQuietly(
                            imageKey,
                            "삭제된 프로필 이미지 정리"
                        );
                    }
                }
            );
    }

    /**
     * 커밋 이후 파일 정리 실패가
     * 사용자 요청 전체를 실패시키지 않도록 로그만 남긴다.
     */
    private void deleteQuietly(
        String imageKey,
        String action
    ) {
        try {
            profileImageStorage.delete(
                imageKey
            );
        } catch (RuntimeException exception) {
            log.error(
                "{} 실패: imageKey={}",
                action,
                imageKey,
                exception
            );
        }
    }
}