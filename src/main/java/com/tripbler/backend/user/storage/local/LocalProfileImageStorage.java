package com.tripbler.backend.user.storage.local;

import com.tripbler.backend.user.exception.InvalidProfileImageException;
import com.tripbler.backend.user.exception.ProfileImageStorageException;
import com.tripbler.backend.user.storage.ProfileImageStorage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.UUID;

/**
 * 개발 환경에서 프로필 이미지를
 * Spring Boot 서버의 로컬 디스크에 저장한다.
 */
@Component
@ConditionalOnProperty(
    name = "profile-image.storage.type",
    havingValue = "local",
    matchIfMissing = true
)
public class LocalProfileImageStorage
    implements ProfileImageStorage {

    private final Path rootPath;
    private final String publicBaseUrl;

    public LocalProfileImageStorage(
        @Value("${profile-image.storage.local-root}")
        String localRoot,
        @Value("${profile-image.storage.public-base-url}")
        String publicBaseUrl
    ) {
        this.rootPath =
            Paths.get(localRoot)
                .toAbsolutePath()
                .normalize();

        this.publicBaseUrl =
            removeTrailingSlash(publicBaseUrl);
    }

    @Override
    public String save(
        Long userId,
        MultipartFile file
    ) {
        String extension =
            resolveExtension(file);

        String imageKey =
            "profiles/"
                + userId
                + "/"
                + UUID.randomUUID()
                + "."
                + extension;

        Path targetPath =
            rootPath.resolve(imageKey)
                .normalize();

        ensureInsideRoot(targetPath);

        try {
            Files.createDirectories(
                targetPath.getParent()
            );

            file.transferTo(targetPath);

            return imageKey;
        } catch (IOException | IllegalStateException exception) {
            throw new ProfileImageStorageException(
                "프로필 이미지 저장에 실패했습니다.",
                exception
            );
        }
    }

    @Override
    public void delete(
        String imageKey
    ) {
        if (!StringUtils.hasText(imageKey)) {
            return;
        }

        Path targetPath =
            rootPath.resolve(imageKey)
                .normalize();

        ensureInsideRoot(targetPath);

        try {
            Files.deleteIfExists(targetPath);
        } catch (IOException exception) {
            throw new ProfileImageStorageException(
                "프로필 이미지 삭제에 실패했습니다.",
                exception
            );
        }
    }

    @Override
    public String resolveUrl(
        String imageKey
    ) {
        if (!StringUtils.hasText(imageKey)) {
            return null;
        }

        return publicBaseUrl
            + "/"
            + imageKey;
    }

    /**
     * 파일 확장자를 추출한다.
     */
    private String resolveExtension(
        MultipartFile file
    ) {
        String extension =
            StringUtils.getFilenameExtension(
                file.getOriginalFilename()
            );

        if (!StringUtils.hasText(extension)) {
            throw new InvalidProfileImageException(
                "프로필 이미지 파일 확장자가 없습니다."
            );
        }

        return extension.toLowerCase(
            Locale.ROOT
        );
    }

    /**
     * 저장 경로가 설정된 루트 디렉터리를
     * 벗어나지 않는지 확인한다.
     */
    private void ensureInsideRoot(
        Path targetPath
    ) {
        if (!targetPath.startsWith(rootPath)) {
            throw new ProfileImageStorageException(
                "허용되지 않은 프로필 이미지 저장 경로입니다."
            );
        }
    }

    private String removeTrailingSlash(
        String value
    ) {
        if (value.endsWith("/")) {
            return value.substring(
                0,
                value.length() - 1
            );
        }

        return value;
    }
}