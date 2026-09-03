package com.tripbler.backend.auth.repository;

import com.tripbler.backend.auth.entity.PasswordResetVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PasswordResetVerificationRepository
    extends JpaRepository<PasswordResetVerification, Long> {

    // 아이디 + 이메일 조합으로 가장 최근 발급된 인증 정보를 조회한다.
    Optional<PasswordResetVerification>
        findTopByLoginIdIgnoreCaseAndEmailIgnoreCaseOrderByIdDesc(
            String loginId,
            String email
        );

    // 재발급 시 해당 아이디 + 이메일의 기존 인증 정보를 모두 삭제한다.
    void deleteAllByLoginIdIgnoreCaseAndEmailIgnoreCase(
        String loginId,
        String email
    );

    // 탈퇴하는 사용자의 비밀번호 재설정 인증 정보를 모두 삭제한다.
    void deleteAllByLoginIdIgnoreCase(
        String loginId
    );
}