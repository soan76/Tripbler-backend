package com.tripbler.backend.auth.service;

import com.tripbler.backend.auth.entity.FindIdVerification;
import com.tripbler.backend.auth.repository.FindIdVerificationRepository;
import com.tripbler.backend.common.mail.EmailService;
import com.tripbler.backend.common.exception.BusinessException;
import com.tripbler.backend.common.exception.ErrorCode;
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
public class FindIdVerificationService {

    private static final Logger log = LoggerFactory.getLogger(FindIdVerificationService.class);

    private static final int CODE_BOUND = 1_000_000;
    private static final long CODE_EXPIRATION_MINUTES = 5;

    private final FindIdVerificationRepository verificationRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactionTemplate;
    private final AuthRateLimiter authRateLimiter;

    private final SecureRandom secureRandom = new SecureRandom();

    public FindIdVerificationService(
        FindIdVerificationRepository verificationRepository,
        SocialAccountRepository socialAccountRepository,
        EmailService emailService,
        PasswordEncoder passwordEncoder,
        PlatformTransactionManager transactionManager,
        AuthRateLimiter authRateLimiter
    ) {
        this.verificationRepository = verificationRepository;
        this.socialAccountRepository = socialAccountRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.authRateLimiter = authRateLimiter;
    }

    // 연동 이메일이 존재하면 아이디 찾기 인증코드를 생성하고 발송한다.
    //
    // 계정 존재가 드러나지 않도록, 요청 제한 자리는 계정 조회 전에 확보하고
    // 가입 여부나 발송 성공 여부와 상관없이 같은 방식으로 끝낸다(한도 초과만 429).
    public void sendVerificationCode(
        String email,
        String clientIp
    ) {
        authRateLimiter.acquireSendCode(clientIp, email);

        try {
            transactionTemplate.executeWithoutResult(
                status -> issueAndSendCode(email)
            );
        } catch (MailException exception) {
            // 발급은 롤백되어 이전 코드가 남는다. 한도 차감은 되돌리지 않는다.
            // 예외 메시지에 받는 사람 주소가 담길 수 있어 종류만 남긴다.
            log.warn(
                "아이디 찾기 인증 메일 발송 실패: {}",
                exception.getClass().getSimpleName()
            );
        }
    }

    // 기존 코드 삭제, 새 코드 저장, 메일 발송을 한 트랜잭션에서 처리한다.
    // 발송이 실패하면 예외가 트랜잭션 밖으로 나가 삭제·저장이 함께 롤백된다.
    private void issueAndSendCode(String email) {
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
        VerifyOutcome outcome = transactionTemplate.execute(
            status -> verifyInTransaction(email, code)
        );

        if (outcome.error() != null) {
            throw new BusinessException(outcome.error());
        }

        return outcome.loginId();
    }

    // 인증코드 행을 잠근 채 판정하고 결과를 돌려준다.
    // 오답 기록이 커밋되도록 여기서는 예외를 던지지 않는다.
    // 레코드 없음·사용됨·만료·폐기·오답 등 모든 실패를 같은 코드로 응답한다.
    // 실패 종류가 다르면 그 차이로 이메일이 연동 계정인지 알아낼 수 있기 때문이다.
    private VerifyOutcome verifyInTransaction(
        String email,
        String code
    ) {
        Optional<FindIdVerification> found = verificationRepository
            .findFirstByEmailIgnoreCaseOrderByIdDesc(email);

        if (found.isEmpty()) {
            return VerifyOutcome.failure(
                ErrorCode.INVALID_FIND_ID_VERIFICATION_CODE
            );
        }

        FindIdVerification verification = found.get();

        if (verification.isVerified()) {
            return VerifyOutcome.failure(
                ErrorCode.INVALID_FIND_ID_VERIFICATION_CODE
            );
        }

        if (verification.isExpired()) {
            return VerifyOutcome.failure(
                ErrorCode.INVALID_FIND_ID_VERIFICATION_CODE
            );
        }

        if (verification.isAttemptsExhausted()) {
            return VerifyOutcome.failure(
                ErrorCode.INVALID_FIND_ID_VERIFICATION_CODE
            );
        }

        if (!passwordEncoder.matches(
            code,
            verification.getCodeHash()
        )) {
            verification.recordFailedAttempt();

            // 5번째 오답으로 폐기돼도 응답은 같다(폐기 여부로 계정 존재가 드러나지 않게).
            return VerifyOutcome.failure(
                ErrorCode.INVALID_FIND_ID_VERIFICATION_CODE
            );
        }

        Optional<SocialAccount> socialAccount = socialAccountRepository
            .findByProviderAndProviderEmailIgnoreCase(
                SocialProvider.GOOGLE,
                email
            );

        if (socialAccount.isEmpty()) {
            return VerifyOutcome.failure(
                ErrorCode.INVALID_FIND_ID_VERIFICATION_CODE
            );
        }

        verification.markVerified();

        return VerifyOutcome.success(
            socialAccount.get()
                .getUser()
                .getLoginId()
        );
    }

    private String generateVerificationCode() {
        int number = secureRandom.nextInt(CODE_BOUND);

        return String.format("%06d", number);
    }

    private record VerifyOutcome(
        String loginId,
        ErrorCode error
    ) {

        static VerifyOutcome success(String loginId) {
            return new VerifyOutcome(loginId, null);
        }

        static VerifyOutcome failure(ErrorCode error) {
            return new VerifyOutcome(null, error);
        }
    }
}
