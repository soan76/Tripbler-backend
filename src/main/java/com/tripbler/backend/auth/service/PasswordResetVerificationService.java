package com.tripbler.backend.auth.service;

import com.tripbler.backend.auth.entity.PasswordResetVerification;
import com.tripbler.backend.auth.repository.PasswordResetVerificationRepository;
import com.tripbler.backend.common.exception.BusinessException;
import com.tripbler.backend.common.exception.ErrorCode;
import com.tripbler.backend.common.mail.EmailService;
import com.tripbler.backend.user.entity.SocialAccount;
import com.tripbler.backend.user.entity.SocialProvider;
import com.tripbler.backend.user.repository.SocialAccountRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
@Transactional
public class PasswordResetVerificationService {

    private static final int CODE_BOUND = 1_000_000;
    private static final long CODE_EXPIRATION_MINUTES = 5;

    private final PasswordResetVerificationRepository verificationRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetTokenService resetTokenService;

    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetVerificationService(
        PasswordResetVerificationRepository verificationRepository,
        SocialAccountRepository socialAccountRepository,
        EmailService emailService,
        PasswordEncoder passwordEncoder,
        PasswordResetTokenService resetTokenService
    ) {
        this.verificationRepository = verificationRepository;
        this.socialAccountRepository = socialAccountRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.resetTokenService = resetTokenService;
    }

    // 입력한 loginId와 Google 연동 이메일이 같은 Tripbler 계정이면
    // 비밀번호 재설정 인증코드를 생성하고 이메일로 발송한다.
    public void sendVerificationCode(
        String loginId,
        String email
    ) {
        Optional<SocialAccount> socialAccount =
            socialAccountRepository.findByProviderAndProviderEmailIgnoreCase(
                SocialProvider.GOOGLE,
                email
            );

        // 존재 여부를 외부에 노출하지 않는다.
        if (socialAccount.isEmpty()) {
            return;
        }

        SocialAccount account = socialAccount.get();

        // 입력한 loginId와 해당 Google 계정에 연결된
        // Tripbler 사용자가 서로 다른 경우에도 동일하게 종료한다.
        if (!account.getUser().getLoginId().equals(loginId)) {
            return;
        }

        String recipientEmail = account.getProviderEmail();

        // 같은 아이디 + 이메일 조합으로 기존에 발급한
        // 인증코드가 있다면 모두 삭제한다.
        verificationRepository
            .deleteAllByLoginIdIgnoreCaseAndEmailIgnoreCase(
                loginId,
                recipientEmail
            );

        String verificationCode = generateVerificationCode();

        String codeHash =
            passwordEncoder.encode(verificationCode);

        Instant expiresAt = Instant.now()
            .plus(
                CODE_EXPIRATION_MINUTES,
                ChronoUnit.MINUTES
            );

        PasswordResetVerification verification =
            new PasswordResetVerification(
                loginId,
                recipientEmail,
                codeHash,
                expiresAt
            );

        verificationRepository.save(verification);

        emailService.sendPasswordResetVerificationCode(
            recipientEmail,
            verificationCode
        );
    }

    // 인증코드를 검증하고,
    // 인증에 성공한 Tripbler 사용자의 ID를 반환한다.
    //
    // 반환된 userId는 이후 비밀번호 재설정용
    // 일회성 resetToken 발급에 사용한다.
    public String verifyCode(
        String loginId,
        String email,
        String code
    ) {
        PasswordResetVerification verification =
            verificationRepository
                .findTopByLoginIdIgnoreCaseAndEmailIgnoreCaseOrderByIdDesc(
                    loginId,
                    email
                )
                .orElseThrow(
                    () -> new BusinessException(
                        ErrorCode.INVALID_PASSWORD_RESET_VERIFICATION_CODE
                    )
                );

        // 이미 사용한 인증코드는 다시 사용할 수 없다.
        if (verification.isVerified()) {
            throw new BusinessException(
                ErrorCode.INVALID_PASSWORD_RESET_VERIFICATION_CODE
            );
        }

        // 인증코드 만료 확인
        if (verification.isExpired()) {
            throw new BusinessException(
                ErrorCode.EXPIRED_PASSWORD_RESET_VERIFICATION_CODE
            );
        }

        // 입력 코드와 DB에 저장된 해시 비교
        if (!passwordEncoder.matches(
            code,
            verification.getCodeHash()
        )) {
            verification.increaseAttemptCount();

            throw new BusinessException(
                ErrorCode.INVALID_PASSWORD_RESET_VERIFICATION_CODE
            );
        }

        // 인증코드가 맞더라도 마지막으로 실제 Google 연동 계정과
        // Tripbler loginId 조합을 다시 확인한다.
        SocialAccount socialAccount =
            socialAccountRepository
                .findByProviderAndProviderEmailIgnoreCase(
                    SocialProvider.GOOGLE,
                    email
                )
                .orElseThrow(
                    () -> new BusinessException(
                        ErrorCode.INVALID_PASSWORD_RESET_VERIFICATION_CODE
                    )
                );

        if (!socialAccount
            .getUser()
            .getLoginId()
            .equals(loginId)) {

            throw new BusinessException(
                ErrorCode.INVALID_PASSWORD_RESET_VERIFICATION_CODE
            );
        }

        verification.markVerified();

        Long userId = socialAccount
            .getUser()
            .getId();

        return resetTokenService.createResetToken(userId);
    }

    private String generateVerificationCode() {
        int number = secureRandom.nextInt(CODE_BOUND);

        return String.format("%06d", number);
    }
}