package com.tripbler.backend.auth.service;

import com.tripbler.backend.auth.entity.PasswordResetVerification;
import com.tripbler.backend.auth.repository.PasswordResetVerificationRepository;
import com.tripbler.backend.common.exception.BusinessException;
import com.tripbler.backend.common.exception.ErrorCode;
import com.tripbler.backend.common.mail.EmailService;
import com.tripbler.backend.user.entity.SocialAccount;
import com.tripbler.backend.user.entity.SocialProvider;
import com.tripbler.backend.user.entity.User;
import com.tripbler.backend.user.repository.SocialAccountRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.mail.MailSendException;
import org.mockito.InOrder;
import com.tripbler.backend.common.exception.TooManyRequestsException;
import com.tripbler.backend.common.ratelimit.AuthRateLimiter;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetVerificationServiceTest {

    @Mock
    private PasswordResetVerificationRepository verificationRepository;

    @Mock
    private SocialAccountRepository socialAccountRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PasswordResetTokenService resetTokenService;

    @Mock
    private AuthRateLimiter authRateLimiter;

    private static final String CLIENT_IP = "10.0.0.1";
    private static final String DUMMY_HASH = "dummy-hash";

    private PasswordResetVerificationService service;

    @BeforeEach
    void setUp() {
        // 서비스 생성 때 시간 맞춤용 더미 해시를 한 번 만든다.
        when(passwordEncoder.encode(anyString())).thenReturn(DUMMY_HASH);

        service = new PasswordResetVerificationService(
            verificationRepository,
            socialAccountRepository,
            emailService,
            passwordEncoder,
            resetTokenService,
            mock(PlatformTransactionManager.class),
            authRateLimiter
        );
        clearInvocations(passwordEncoder);
    }

    @Test
    void sendAcquiresLimitBeforeAccountLookup() {
        String email = "test@gmail.com";
        when(
            socialAccountRepository.findByProviderAndProviderEmailIgnoreCase(
                SocialProvider.GOOGLE,
                email
            )
        ).thenReturn(Optional.empty());

        service.sendVerificationCode("testuser", email, CLIENT_IP);

        InOrder inOrder = inOrder(authRateLimiter, socialAccountRepository);
        inOrder.verify(authRateLimiter).acquireSendCode(CLIENT_IP, email);
        inOrder.verify(socialAccountRepository)
            .findByProviderAndProviderEmailIgnoreCase(SocialProvider.GOOGLE, email);
    }

    @Test
    void rateLimitedRequestDoesNotLookUpAccount() {
        doThrow(new TooManyRequestsException(30))
            .when(authRateLimiter).acquireSendCode(CLIENT_IP, "test@gmail.com");

        TooManyRequestsException exception = assertThrows(
            TooManyRequestsException.class,
            () -> service.sendVerificationCode("testuser", "test@gmail.com", CLIENT_IP)
        );

        assertEquals(30, exception.getRetryAfterSeconds());
        verifyNoInteractions(socialAccountRepository, verificationRepository, emailService);
    }

    @Test
    void mailFailureIsSwallowed() {
        SocialAccount socialAccount = mock(SocialAccount.class);
        User user = mock(User.class);
        when(user.getLoginId()).thenReturn("testuser");
        when(socialAccount.getUser()).thenReturn(user);
        when(socialAccount.getProviderEmail()).thenReturn("test@gmail.com");
        when(
            socialAccountRepository.findByProviderAndProviderEmailIgnoreCase(
                SocialProvider.GOOGLE,
                "test@gmail.com"
            )
        ).thenReturn(Optional.of(socialAccount));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-code");
        doThrow(new MailSendException("smtp down"))
            .when(emailService).sendPasswordResetVerificationCode(anyString(), anyString());

        assertDoesNotThrow(() -> service.sendVerificationCode("testuser", "test@gmail.com", CLIENT_IP));
    }

    @Test
    void fifthWrongAnswerDiscardsCode() {
        String loginId = "testuser";
        String email = "test@gmail.com";

        PasswordResetVerification verification =
            mock(PasswordResetVerification.class);

        when(
            verificationRepository
                .findFirstByLoginIdIgnoreCaseAndEmailIgnoreCaseOrderByIdDesc(
                    loginId,
                    email
                )
        ).thenReturn(Optional.of(verification));
        when(verification.getCodeHash()).thenReturn("hashed-code");
        when(passwordEncoder.matches("111111", "hashed-code"))
            .thenReturn(false);
        when(verification.isAttemptsExhausted())
            .thenReturn(false, true);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> service.verifyCode(loginId, email, "111111")
        );

        assertEquals(
            ErrorCode.INVALID_PASSWORD_RESET_VERIFICATION_CODE,
            exception.getErrorCode()
        );
        verify(verification).recordFailedAttempt();
    }

    @Test
    void discardedCodeRejectsCorrectAnswerWithoutRecording() {
        String loginId = "testuser";
        String email = "test@gmail.com";

        PasswordResetVerification verification =
            mock(PasswordResetVerification.class);

        when(
            verificationRepository
                .findFirstByLoginIdIgnoreCaseAndEmailIgnoreCaseOrderByIdDesc(
                    loginId,
                    email
                )
        ).thenReturn(Optional.of(verification));
        when(verification.isAttemptsExhausted()).thenReturn(true);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> service.verifyCode(loginId, email, "123456")
        );

        assertEquals(
            ErrorCode.INVALID_PASSWORD_RESET_VERIFICATION_CODE,
            exception.getErrorCode()
        );
        verify(verification, never()).recordFailedAttempt();
        // 실패 분기도 더미 해시로 비교를 정확히 한 번 거친다.
        verify(passwordEncoder).matches("123456", DUMMY_HASH);
        verify(passwordEncoder, times(1)).matches(anyString(), anyString());
        verifyNoInteractions(socialAccountRepository, resetTokenService);
    }

    @Test
    void verifyCodeFailsWhenRecordIsMissing() {
        String loginId = "testuser";
        String email = "test@gmail.com";

        when(
            verificationRepository
                .findFirstByLoginIdIgnoreCaseAndEmailIgnoreCaseOrderByIdDesc(
                    loginId,
                    email
                )
        ).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> service.verifyCode(loginId, email, "123456")
        );

        assertEquals(
            ErrorCode.INVALID_PASSWORD_RESET_VERIFICATION_CODE,
            exception.getErrorCode()
        );

        // 실패 분기도 더미 해시로 비교를 정확히 한 번 거친다.
        verify(passwordEncoder).matches("123456", DUMMY_HASH);
        verify(passwordEncoder, times(1)).matches(anyString(), anyString());
        verifyNoInteractions(socialAccountRepository, resetTokenService);
    }

    @Test
    void sendVerificationCodeSucceeds() {
        String loginId = "testuser";
        String inputEmail = "Test@gmail.com";
        String storedEmail = "test@gmail.com";

        User user = mock(User.class);
        SocialAccount socialAccount = mock(SocialAccount.class);

        when(
            socialAccountRepository
                .findByProviderAndProviderEmailIgnoreCase(
                    SocialProvider.GOOGLE,
                    inputEmail
                )
        ).thenReturn(Optional.of(socialAccount));

        when(socialAccount.getUser())
            .thenReturn(user);

        when(user.getLoginId())
            .thenReturn(loginId);

        when(socialAccount.getProviderEmail())
            .thenReturn(storedEmail);

        when(passwordEncoder.encode(anyString()))
            .thenReturn("hashed-code");

        Instant before = Instant.now();

        service.sendVerificationCode(
            loginId,
            inputEmail,
            CLIENT_IP
        );

        Instant after = Instant.now();

        verify(verificationRepository)
            .deleteAllByLoginIdIgnoreCaseAndEmailIgnoreCase(
                loginId,
                storedEmail
            );

        ArgumentCaptor<String> codeCaptor =
            ArgumentCaptor.forClass(String.class);

        verify(passwordEncoder)
            .encode(codeCaptor.capture());

        String verificationCode =
            codeCaptor.getValue();

        assertTrue(
            verificationCode.matches("\\d{6}")
        );

        ArgumentCaptor<PasswordResetVerification>
            verificationCaptor =
                ArgumentCaptor.forClass(
                    PasswordResetVerification.class
                );

        verify(verificationRepository)
            .save(verificationCaptor.capture());

        PasswordResetVerification saved =
            verificationCaptor.getValue();

        assertEquals(
            loginId,
            saved.getLoginId()
        );

        assertEquals(
            storedEmail,
            saved.getEmail()
        );

        assertEquals(
            "hashed-code",
            saved.getCodeHash()
        );

        assertFalse(saved.isVerified());

        assertEquals(
            0,
            saved.getAttemptCount()
        );

        assertFalse(
            saved.getExpiresAt().isBefore(
                before.plus(
                    5,
                    ChronoUnit.MINUTES
                )
            )
        );

        assertFalse(
            saved.getExpiresAt().isAfter(
                after.plus(
                    5,
                    ChronoUnit.MINUTES
                )
            )
        );

        verify(emailService)
            .sendPasswordResetVerificationCode(
                storedEmail,
                verificationCode
            );
    }

    @Test
    void sendVerificationCodeDoesNothingWhenEmailIsNotLinked() {
        String loginId = "testuser";
        String email = "unknown@gmail.com";

        when(
            socialAccountRepository
                .findByProviderAndProviderEmailIgnoreCase(
                    SocialProvider.GOOGLE,
                    email
                )
        ).thenReturn(Optional.empty());

        service.sendVerificationCode(
            loginId,
            email,
            CLIENT_IP
        );

        verifyNoInteractions(
            verificationRepository,
            passwordEncoder,
            emailService,
            resetTokenService
        );
    }

    @Test
    void sendVerificationCodeDoesNothingWhenLoginIdDoesNotMatch() {
        String inputLoginId = "wronguser";
        String email = "test@gmail.com";

        User user = mock(User.class);
        SocialAccount socialAccount = mock(SocialAccount.class);

        when(
            socialAccountRepository
                .findByProviderAndProviderEmailIgnoreCase(
                    SocialProvider.GOOGLE,
                    email
                )
        ).thenReturn(Optional.of(socialAccount));

        when(socialAccount.getUser())
            .thenReturn(user);

        when(user.getLoginId())
            .thenReturn("testuser");

        service.sendVerificationCode(
            inputLoginId,
            email,
            CLIENT_IP
        );

        verifyNoInteractions(
            verificationRepository,
            passwordEncoder,
            emailService,
            resetTokenService
        );
    }

    @Test
    void verifyCodeSucceedsAndReturnsResetToken() {
        String loginId = "testuser";
        String email = "test@gmail.com";
        String code = "123456";

        PasswordResetVerification verification =
            mock(PasswordResetVerification.class);

        SocialAccount socialAccount =
            mock(SocialAccount.class);

        User user =
            mock(User.class);

        when(
            verificationRepository
                .findFirstByLoginIdIgnoreCaseAndEmailIgnoreCaseOrderByIdDesc(
                    loginId,
                    email
                )
        ).thenReturn(Optional.of(verification));

        when(verification.isVerified())
            .thenReturn(false);

        when(verification.isExpired())
            .thenReturn(false);

        when(verification.getCodeHash())
            .thenReturn("hashed-code");

        when(
            passwordEncoder.matches(
                code,
                "hashed-code"
            )
        ).thenReturn(true);

        when(
            socialAccountRepository
                .findByProviderAndProviderEmailIgnoreCase(
                    SocialProvider.GOOGLE,
                    email
                )
        ).thenReturn(Optional.of(socialAccount));

        when(socialAccount.getUser())
            .thenReturn(user);

        when(user.getLoginId())
            .thenReturn(loginId);

        when(user.getId())
            .thenReturn(1L);

        when(
            resetTokenService.createResetToken(1L)
        ).thenReturn("reset-token");

        String resetToken =
            service.verifyCode(
                loginId,
                email,
                code
            );

        assertEquals(
            "reset-token",
            resetToken
        );

        verify(verification)
            .markVerified();

        verify(resetTokenService)
            .createResetToken(1L);
    }

    @Test
    void verifyCodeFailsWhenCodeIsIncorrect() {
        String loginId = "testuser";
        String email = "test@gmail.com";
        String code = "111111";

        PasswordResetVerification verification =
            mock(PasswordResetVerification.class);

        when(
            verificationRepository
                .findFirstByLoginIdIgnoreCaseAndEmailIgnoreCaseOrderByIdDesc(
                    loginId,
                    email
                )
        ).thenReturn(Optional.of(verification));

        when(verification.isVerified())
            .thenReturn(false);

        when(verification.isExpired())
            .thenReturn(false);

        when(verification.getCodeHash())
            .thenReturn("hashed-code");

        when(
            passwordEncoder.matches(
                code,
                "hashed-code"
            )
        ).thenReturn(false);

        BusinessException exception =
            assertThrows(
                BusinessException.class,
                () -> service.verifyCode(
                    loginId,
                    email,
                    code
                )
            );

        assertEquals(
            ErrorCode.INVALID_PASSWORD_RESET_VERIFICATION_CODE,
            exception.getErrorCode()
        );

        verify(verification)
            .recordFailedAttempt();

        verify(passwordEncoder, times(1)).matches(anyString(), anyString());
        verify(passwordEncoder, never()).matches(anyString(), eq(DUMMY_HASH));
        verifyNoInteractions(
            socialAccountRepository,
            resetTokenService
        );
    }

    @Test
    void verifyCodeFailsWhenCodeIsExpired() {
        String loginId = "testuser";
        String email = "test@gmail.com";

        PasswordResetVerification verification =
            mock(PasswordResetVerification.class);

        when(
            verificationRepository
                .findFirstByLoginIdIgnoreCaseAndEmailIgnoreCaseOrderByIdDesc(
                    loginId,
                    email
                )
        ).thenReturn(Optional.of(verification));

        when(verification.isVerified())
            .thenReturn(false);

        when(verification.isExpired())
            .thenReturn(true);

        BusinessException exception =
            assertThrows(
                BusinessException.class,
                () -> service.verifyCode(
                    loginId,
                    email,
                    "123456"
                )
            );

        assertEquals(
            ErrorCode.INVALID_PASSWORD_RESET_VERIFICATION_CODE,
            exception.getErrorCode()
        );

        // 실패 분기도 더미 해시로 비교를 정확히 한 번 거친다.
        verify(passwordEncoder).matches("123456", DUMMY_HASH);
        verify(passwordEncoder, times(1)).matches(anyString(), anyString());
        verifyNoInteractions(socialAccountRepository, resetTokenService);
    }

    @Test
    void verifyCodeFailsWhenCodeWasAlreadyUsed() {
        String loginId = "testuser";
        String email = "test@gmail.com";

        PasswordResetVerification verification =
            mock(PasswordResetVerification.class);

        when(
            verificationRepository
                .findFirstByLoginIdIgnoreCaseAndEmailIgnoreCaseOrderByIdDesc(
                    loginId,
                    email
                )
        ).thenReturn(Optional.of(verification));

        when(verification.isVerified())
            .thenReturn(true);

        BusinessException exception =
            assertThrows(
                BusinessException.class,
                () -> service.verifyCode(
                    loginId,
                    email,
                    "123456"
                )
            );

        assertEquals(
            ErrorCode.INVALID_PASSWORD_RESET_VERIFICATION_CODE,
            exception.getErrorCode()
        );

        // 실패 분기도 더미 해시로 비교를 정확히 한 번 거친다.
        verify(passwordEncoder).matches("123456", DUMMY_HASH);
        verify(passwordEncoder, times(1)).matches(anyString(), anyString());
        verifyNoInteractions(socialAccountRepository, resetTokenService);
    }
}