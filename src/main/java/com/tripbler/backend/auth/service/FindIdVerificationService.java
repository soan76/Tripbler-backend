package com.tripbler.backend.auth.service;

import com.tripbler.backend.auth.entity.FindIdVerification;
import com.tripbler.backend.auth.repository.FindIdVerificationRepository;
import com.tripbler.backend.common.mail.EmailService;
import com.tripbler.backend.common.exception.BusinessException;
import com.tripbler.backend.common.exception.ErrorCode;
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
public class FindIdVerificationService {

    private static final int CODE_BOUND = 1_000_000;
    private static final long CODE_EXPIRATION_MINUTES = 5;

    private final FindIdVerificationRepository verificationRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    private final SecureRandom secureRandom = new SecureRandom();

    public FindIdVerificationService(
        FindIdVerificationRepository verificationRepository,
        SocialAccountRepository socialAccountRepository,
        EmailService emailService,
        PasswordEncoder passwordEncoder
    ) {
        this.verificationRepository = verificationRepository;
        this.socialAccountRepository = socialAccountRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
    }

    // 연동 이메일이 존재하면 아이디 찾기 인증코드를 생성하고 발송한다.
    public void sendVerificationCode(String email) {
        Optional<SocialAccount> socialAccount =
            socialAccountRepository.findByProviderAndProviderEmailIgnoreCase(
                SocialProvider.GOOGLE,
                email
            );

        if (socialAccount.isEmpty()) {
            return;
        }

        String recipientEmail =
            socialAccount.get().getProviderEmail();

        verificationRepository.deleteAllByEmailIgnoreCase(
            recipientEmail
        );

        String verificationCode = generateVerificationCode();
        String codeHash = passwordEncoder.encode(verificationCode);

        Instant expiresAt = Instant.now()
            .plus(CODE_EXPIRATION_MINUTES, ChronoUnit.MINUTES);

        FindIdVerification verification =
            new FindIdVerification(
                recipientEmail,
                codeHash,
                expiresAt
            );

        verificationRepository.save(verification);

        emailService.sendFindIdVerificationCode(
            recipientEmail,
            verificationCode
        );
    }

    // 아이디 찾기 인증코드를 검증하고 연결된 Tripbler 아이디를 반환한다.
    public String verifyCode(
        String email,
        String code
    ) {
        FindIdVerification verification = verificationRepository
            .findTopByEmailIgnoreCaseOrderByIdDesc(email)
            .orElseThrow(
                () -> new BusinessException(
                    ErrorCode.INVALID_FIND_ID_VERIFICATION_CODE
                )
            );

        if (verification.isVerified()) {
            throw new BusinessException(
                ErrorCode.INVALID_FIND_ID_VERIFICATION_CODE
            );
        }

        if (verification.isExpired()) {
            throw new BusinessException(
                ErrorCode.EXPIRED_FIND_ID_VERIFICATION_CODE
            );
        }

        if (!passwordEncoder.matches(
            code,
            verification.getCodeHash()
        )) {
            verification.increaseAttemptCount();

            throw new BusinessException(
                ErrorCode.INVALID_FIND_ID_VERIFICATION_CODE
            );
        }

        SocialAccount socialAccount = socialAccountRepository
            .findByProviderAndProviderEmailIgnoreCase(
                SocialProvider.GOOGLE,
                email
            )
            .orElseThrow(
                () -> new BusinessException(
                    ErrorCode.INVALID_FIND_ID_VERIFICATION_CODE
                )
            );

        verification.markVerified();

        return socialAccount
            .getUser()
            .getLoginId();
    }

    private String generateVerificationCode() {
        int number = secureRandom.nextInt(CODE_BOUND);

        return String.format("%06d", number);
    }
}