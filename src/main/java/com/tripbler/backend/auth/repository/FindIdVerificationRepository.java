package com.tripbler.backend.auth.repository;

import com.tripbler.backend.auth.entity.FindIdVerification;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface FindIdVerificationRepository
    extends JpaRepository<FindIdVerification, Long> {

    // 이메일에 가장 최근 발급된 인증 정보를 행 잠금과 함께 조회한다.
    // 같은 코드에 대한 동시 검증 요청을 순서대로 처리하기 위해 쓴다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<FindIdVerification> findFirstByEmailIgnoreCaseOrderByIdDesc(
        String email
    );

    // 재발급 시 기존 인증 정보를 모두 삭제한다.
    void deleteAllByEmailIgnoreCase(
        String email
    );
}