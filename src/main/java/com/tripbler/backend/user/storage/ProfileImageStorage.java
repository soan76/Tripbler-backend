package com.tripbler.backend.user.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * 프로필 이미지의 저장소 접근을 추상화한다.
 *
 * 개발 환경의 로컬 파일 저장소와
 * 배포 환경의 S3/GCS 저장소가 동일한 방식으로 사용된다.
 */
public interface ProfileImageStorage {

    /**
     * 프로필 이미지를 저장하고 저장소 식별 키를 반환한다.
     *
     * 파일의 크기나 타입과 같은 유효성 검증은
     * 서비스 계층에서 처리한다.
     */
    String save(
        Long userId,
        MultipartFile file
    );

    /**
     * 저장소 키에 해당하는 프로필 이미지를 삭제한다.
     *
     * imageKey가 null 또는 빈 문자열이면
     * 아무 작업도 하지 않는다.
     */
    void delete(
        String imageKey
    );

    /**
     * 저장소 키를 클라이언트가 접근 가능한 URL로 변환한다.
     *
     * imageKey가 null 또는 빈 문자열이면 null을 반환한다.
     *
     * 반환 URL은 저장소 구현에 따라 만료될 수 있으므로
     * DB에 저장하거나 장기간 캐시하지 않는다.
     */
    String resolveUrl(
        String imageKey
    );
}