package com.tripbler.backend.auth.service;

import com.tripbler.backend.common.exception.BusinessException;
import com.tripbler.backend.common.exception.ErrorCode;
import com.tripbler.backend.user.entity.User;
import com.tripbler.backend.user.repository.UserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PasswordResetService {

    private final PasswordResetTokenService resetTokenService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public PasswordResetService(
        PasswordResetTokenService resetTokenService,
        UserRepository userRepository,
        PasswordEncoder passwordEncoder
    ) {
        this.resetTokenService = resetTokenService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // 본인인증 후 발급받은 resetToken을 검증하고
    // 새로운 비밀번호로 변경한다.
    public void resetPassword(
        String resetToken,
        String newPassword
    ) {
        Long userId =
            resetTokenService.validateResetToken(resetToken);

        User user = userRepository
            .findById(userId)
            .orElseThrow(
                () -> new BusinessException(
                    ErrorCode.USER_NOT_FOUND
                )
            );

        // 새 비밀번호가 기존 비밀번호와 같은지 확인한다.
        if (passwordEncoder.matches(
            newPassword,
            user.getPassword()
        )) {
            throw new BusinessException(
                ErrorCode.NEW_PASSWORD_SAME_AS_CURRENT
            );
        }

        String encodedPassword =
            passwordEncoder.encode(newPassword);

        user.changePassword(encodedPassword);

        resetTokenService.markTokenUsed(resetToken);
    }
}