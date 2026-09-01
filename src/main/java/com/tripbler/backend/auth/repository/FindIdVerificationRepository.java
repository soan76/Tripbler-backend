package com.tripbler.backend.auth.repository;

import com.tripbler.backend.auth.entity.FindIdVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FindIdVerificationRepository
    extends JpaRepository<FindIdVerification, Long> {

    // 이메일에 가장 최근 발급된 인증 정보를 조회한다.
    Optional<FindIdVerification> findTopByEmailIgnoreCaseOrderByIdDesc(
        String email
    );

    // 재발급 시 기존 인증 정보를 모두 삭제한다.
    void deleteAllByEmailIgnoreCase(
        String email
    );
}