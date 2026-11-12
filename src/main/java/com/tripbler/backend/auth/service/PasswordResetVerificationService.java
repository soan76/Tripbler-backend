package com.tripbler.backend.auth.service;

import com.tripbler.backend.auth.entity.PasswordResetVerification;
import com.tripbler.backend.auth.repository.PasswordResetVerificationRepository;
import com.tripbler.backend.common.exception.BusinessException;
import com.tripbler.backend.common.exception.ErrorCode;
import com.tripbler.backend.common.mail.EmailService;
import com.tripbler.backend.user.entity.SocialAccount;
import com.tripbler.backend.user.entity.SocialProvider;
import com.tripbler.backend.user.repository.SocialAccountRepository;

import com.tripbler.backend.common.ratelimit.AuthRateLimiter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

// 트랜잭션은 TransactionTemplate으로 직접 연다.
// 인증코드 오답은 결과를 커밋한 뒤 트랜잭션 바깥에서 예외로 바꿔야
// 오답 횟수가 롤백되지 않기 때문이다.
@Service
public class PasswordResetVerificationService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetVerificationService.class);

    private static final int CODE_BOUND = 1_000_000;
    private static final long CODE_EXPIRATION_MINUTES = 5;

    private final PasswordResetVerificationRepository verificationRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetTokenService resetTokenService;
    private final TransactionTemplate transactionTemplate;
    private final AuthRateLimiter authRateLimiter;

    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetVerificationService(
        PasswordResetVerificationRepository verificationRepository,
        SocialAccountRepository socialAccountRepository,
        EmailService emailService,
        PasswordEncoder passwordEncoder,
        PasswordResetTokenService resetTokenService,
        PlatformTransactionManager transactionManager,
        AuthRateLimiter authRateLimiter
    ) {
        this.verificationRepository = verificationRepository;
        this.socialAccountRepository = socialAccountRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.resetTokenService = resetTokenService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.authRateLimiter = authRateLimiter;
    }

    // 입력한 loginId와 Google 연동 이메일이 같은 Tripbler 계정이면
    // 비밀번호 재설정 인증코드를 생성하고 이메일로 발송한다.
    //
    // 계정 존재가 드러나지 않도록, 요청 제한 자리는 계정 조회 전에 확보하고
    // 가입 여부나 발송 성공 여부와 상관없이 같은 방식으로 끝낸다(한도 초과만 429).
    public void sendVerificationCode(
        String loginId,
        String email,
        String clientIp
    ) {
        authRateLimiter.acquireSendCode(clientIp, email);

        try {
            transactionTemplate.executeWithoutResult(
                status -> issueAndSendCode(loginId, email)
            );
        } catch (MailException exception) {
            // 발급은 롤백되어 이전 코드가 남는다. 한도 차감은 되돌리지 않는다.
            // 예외 메시지에 받는 사람 주소가 담길 수 있어 종류만 남긴다.
            log.warn(
                "비밀번호 재설정 인증 메일 발송 실패: {}",
                exception.getClass().getSimpleName()
            );
        }
    }

    // 기존 코드 삭제, 새 코드 저장, 메일 발송을 한 트랜잭션에서 처리한다.
    // 발송이 실패하면 예외가 트랜잭션 밖으로 나가 삭제·저장이 함께 롤백된다.
    private void issueAndSendCode(
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

    // 인증코드를 검증하고, 성공하면 비밀번호 재설정용
    // 일회성 resetToken을 발급해 반환한다.
    public String verifyCode(
        String loginId,
        String email,
        String code
    ) {
        VerifyOutcome outcome = transactionTemplate.execute(
            status -> verifyInTransaction(loginId, email, code)
        );

        if (outcome.error() != null) {
            throw new BusinessException(outcome.error());
        }

        return outcome.resetToken();
    }

    // 인증코드 행을 잠근 채 판정하고 결과를 돌려준다.
    // 오답 기록이 커밋되도록 여기서는 예외를 던지지 않는다.
    // 레코드 없음·사용됨·만료·폐기·오답·loginId 불일치 등 모든 실패를 같은 코드로 응답한다.
    // 실패 종류가 다르면 그 차이로 아이디와 이메일이 같은 계정인지 알아낼 수 있기 때문이다.
    private VerifyOutcome verifyInTransaction(
        String loginId,
        String email,
        String code
    ) {
        Optional<PasswordResetVerification> found =
            verificationRepository
                .findFirstByLoginIdIgnoreCaseAndEmailIgnoreCaseOrderByIdDesc(
                    loginId,
                    email
                );

        if (found.isEmpty()) {
            return VerifyOutcome.failure(
                ErrorCode.INVALID_PASSWORD_RESET_VERIFICATION_CODE
            );
        }

        PasswordResetVerification verification = found.get();

        // 이미 사용한 인증코드는 다시 사용할 수 없다.
        if (verification.isVerified()) {
            return VerifyOutcome.failure(
                ErrorCode.INVALID_PASSWORD_RESET_VERIFICATION_CODE
            );
        }

        // 인증코드 만료 확인
        if (verification.isExpired()) {
            return VerifyOutcome.failure(
                ErrorCode.INVALID_PASSWORD_RESET_VERIFICATION_CODE
            );
        }

        // 오답 상한에 도달해 폐기된 코드는 정답이어도 쓸 수 없다.
        if (verification.isAttemptsExhausted()) {
            return VerifyOutcome.failure(
                ErrorCode.INVALID_PASSWORD_RESET_VERIFICATION_CODE
            );
        }

        // 입력 코드와 DB에 저장된 해시 비교
        if (!passwordEncoder.matches(
            code,
            verification.getCodeHash()
        )) {
            verification.recordFailedAttempt();

            // 5번째 오답으로 폐기돼도 응답은 같다(폐기 여부로 계정 존재가 드러나지 않게).
            return VerifyOutcome.failure(
                ErrorCode.INVALID_PASSWORD_RESET_VERIFICATION_CODE
            );
        }

        // 인증코드가 맞더라도 마지막으로 실제 Google 연동 계정과
        // Tripbler loginId 조합을 다시 확인한다.
        Optional<SocialAccount> socialAccount =
            socialAccountRepository
                .findByProviderAndProviderEmailIgnoreCase(
                    SocialProvider.GOOGLE,
                    email
                );

        if (socialAccount.isEmpty()
            || !socialAccount.get()
                .getUser()
                .getLoginId()
                .equals(loginId)) {

            return VerifyOutcome.failure(
                ErrorCode.INVALID_PASSWORD_RESET_VERIFICATION_CODE
            );
        }

        verification.markVerified();

        Long userId = socialAccount.get()
            .getUser()
            .getId();

        return VerifyOutcome.success(
            resetTokenService.createResetToken(userId)
        );
    }

    private String generateVerificationCode() {
        int number = secureRandom.nextInt(CODE_BOUND);

        return String.format("%06d", number);
    }

    private record VerifyOutcome(
        String resetToken,
        ErrorCode error
    ) {

        static VerifyOutcome success(String resetToken) {
            return new VerifyOutcome(resetToken, null);
        }

        static VerifyOutcome failure(ErrorCode error) {
            return new VerifyOutcome(null, error);
        }
    }
}
