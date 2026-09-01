package com.tripbler.backend.auth.service;

import com.tripbler.backend.auth.entity.FindIdVerification;
import com.tripbler.backend.auth.repository.FindIdVerificationRepository;
import com.tripbler.backend.common.mail.EmailService;
import com.tripbler.backend.common.exception.BusinessException;
import com.tripbler.backend.common.exception.ErrorCode;
import com.tripbler.backend.user.entity.User;
import com.tripbler.backend.user.entity.SocialAccount;
import com.tripbler.backend.user.entity.SocialProvider;
import com.tripbler.backend.user.repository.SocialAccountRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FindIdVerificationServiceTest {

    @Mock
    private FindIdVerificationRepository verificationRepository;

    @Mock
    private SocialAccountRepository socialAccountRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private PasswordEncoder passwordEncoder;

    private FindIdVerificationService service;

    @BeforeEach
    void setUp() {
        service = new FindIdVerificationService(
            verificationRepository,
            socialAccountRepository,
            emailService,
            passwordEncoder
        );
    }

    @Test
    void verifyCodeSucceeds() {
        String email = "test@gmail.com";
        String code = "123456";

        FindIdVerification verification =
            mock(FindIdVerification.class);

        SocialAccount socialAccount =
            mock(SocialAccount.class);

        User user = mock(User.class);

        when(
            verificationRepository
                .findTopByEmailIgnoreCaseOrderByIdDesc(email)
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
            .thenReturn("testuser");

        String loginId =
            service.verifyCode(email, code);

        assertEquals("testuser", loginId);

        verify(verification).markVerified();
    }

    @Test
    void verifyCodeFailsWhenCodeIsIncorrect() {
        String email = "test@gmail.com";
        String code = "111111";

        FindIdVerification verification =
            mock(FindIdVerification.class);

        when(
            verificationRepository
                .findTopByEmailIgnoreCaseOrderByIdDesc(email)
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

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> service.verifyCode(email, code)
        );

        assertEquals(
            ErrorCode.INVALID_FIND_ID_VERIFICATION_CODE,
            exception.getErrorCode()
        );

        verify(verification)
            .increaseAttemptCount();

        verifyNoInteractions(socialAccountRepository);
    }

    @Test
    void verifyCodeFailsWhenCodeIsExpired() {
        String email = "test@gmail.com";

        FindIdVerification verification =
            mock(FindIdVerification.class);

        when(
            verificationRepository
                .findTopByEmailIgnoreCaseOrderByIdDesc(email)
        ).thenReturn(Optional.of(verification));

        when(verification.isVerified())
            .thenReturn(false);

        when(verification.isExpired())
            .thenReturn(true);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> service.verifyCode(
                email,
                "123456"
            )
        );

        assertEquals(
            ErrorCode.EXPIRED_FIND_ID_VERIFICATION_CODE,
            exception.getErrorCode()
        );

        verifyNoInteractions(
            passwordEncoder,
            socialAccountRepository
        );
    }

    @Test
    void verifyCodeFailsWhenCodeWasAlreadyUsed() {
        String email = "test@gmail.com";

        FindIdVerification verification =
            mock(FindIdVerification.class);

        when(
            verificationRepository
                .findTopByEmailIgnoreCaseOrderByIdDesc(email)
        ).thenReturn(Optional.of(verification));

        when(verification.isVerified())
            .thenReturn(true);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> service.verifyCode(
                email,
                "123456"
            )
        );

        assertEquals(
            ErrorCode.INVALID_FIND_ID_VERIFICATION_CODE,
            exception.getErrorCode()
        );

        verifyNoInteractions(
            passwordEncoder,
            socialAccountRepository
        );
    }

    @Test
    void sendVerificationCodeSucceeds() {
        String inputEmail = "Test@gmail.com";
        String storedEmail = "test@gmail.com";
        String codeHash = "hashed-code";

        SocialAccount socialAccount = mock(SocialAccount.class);

        when(
            socialAccountRepository
                .findByProviderAndProviderEmailIgnoreCase(
                    SocialProvider.GOOGLE,
                    inputEmail
                )
        ).thenReturn(Optional.of(socialAccount));

        when(socialAccount.getProviderEmail())
            .thenReturn(storedEmail);

        when(passwordEncoder.encode(anyString()))
            .thenReturn(codeHash);

        Instant before = Instant.now();

        service.sendVerificationCode(inputEmail);

        Instant after = Instant.now();

        ArgumentCaptor<String> codeCaptor =
            ArgumentCaptor.forClass(String.class);

        verify(passwordEncoder)
            .encode(codeCaptor.capture());

        String verificationCode = codeCaptor.getValue();

        assertTrue(
            verificationCode.matches("\\d{6}")
        );

        ArgumentCaptor<FindIdVerification> verificationCaptor =
            ArgumentCaptor.forClass(FindIdVerification.class);

        verify(verificationRepository)
            .save(verificationCaptor.capture());

        FindIdVerification saved =
            verificationCaptor.getValue();

        assertEquals(
            storedEmail,
            saved.getEmail()
        );

        assertEquals(
            codeHash,
            saved.getCodeHash()
        );

        assertFalse(saved.isVerified());
        assertEquals(0, saved.getAttemptCount());

        assertFalse(
            saved.getExpiresAt().isBefore(
                before.plus(5, ChronoUnit.MINUTES)
            )
        );

        assertFalse(
            saved.getExpiresAt().isAfter(
                after.plus(5, ChronoUnit.MINUTES)
            )
        );

        verify(emailService)
            .sendFindIdVerificationCode(
                storedEmail,
                verificationCode
            );

        InOrder inOrder = inOrder(
            socialAccountRepository,
            verificationRepository,
            passwordEncoder,
            emailService
        );

        inOrder.verify(socialAccountRepository)
            .findByProviderAndProviderEmailIgnoreCase(
                SocialProvider.GOOGLE,
                inputEmail
            );

        inOrder.verify(verificationRepository)
            .deleteAllByEmailIgnoreCase(storedEmail);

        inOrder.verify(passwordEncoder)
            .encode(verificationCode);

        inOrder.verify(verificationRepository)
            .save(any(FindIdVerification.class));

        inOrder.verify(emailService)
            .sendFindIdVerificationCode(
                storedEmail,
                verificationCode
            );
    }

    @Test
    void sendVerificationCodeDoesNothingWhenEmailIsNotLinked() {
        String email = "unknown@gmail.com";

        when(
            socialAccountRepository
                .findByProviderAndProviderEmailIgnoreCase(
                    SocialProvider.GOOGLE,
                    email
                )
        ).thenReturn(Optional.empty());

        service.sendVerificationCode(email);

        verify(socialAccountRepository)
            .findByProviderAndProviderEmailIgnoreCase(
                SocialProvider.GOOGLE,
                email
            );

        verifyNoInteractions(
            verificationRepository,
            passwordEncoder,
            emailService
        );
    }
}